package com.nurture.settlement.app;

import com.nurture.order.app.OrderApplicationService.OrderSnapshot;
import com.nurture.settlement.domain.SettlementEntry;
import com.nurture.settlement.infra.SettlementEntryRepository;
import com.nurture.shared.config.PlatformConfigReader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * 资金结算应用服务 — 课酬核算、平台抽成与提现流水。
 */
@Service
public class SettlementApplicationService {

    private final SettlementEntryRepository settlementRepository;
    private final PlatformConfigReader configReader;
    private final com.nurture.shared.audit.AuditService auditService;

    public SettlementApplicationService(SettlementEntryRepository settlementRepository,
                                        PlatformConfigReader configReader,
                                        com.nurture.shared.audit.AuditService auditService) {
        this.settlementRepository = settlementRepository;
        this.configReader = configReader;
        this.auditService = auditService;
    }

    /** 供支付域在订单支付成功后调用：按平台抽成生成课酬结算条目。 */
    @Transactional
    public SettlementEntry createEntryForPaidOrder(OrderSnapshot order) {
        BigDecimal feeRate = configReader.decimalValue("settlement.platform-fee-rate", BigDecimal.valueOf(0.15));
        long fee = BigDecimal.valueOf(order.getAmountCents())
                .multiply(feeRate)
                .setScale(0, java.math.RoundingMode.HALF_UP)
                .longValueExact();
        return settlementRepository.save(new SettlementEntry(order.getTeacherId(), order.getBookingId(),
                order.getId(), order.getAmountCents(), fee));
    }

    /** 必须与退款入账处于同一事务；锁定原收益以串行化并发红冲。 */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void reverseForRefund(OrderSnapshot order) {
        SettlementEntry earning = settlementRepository.lockEarning(order.getId(), SettlementEntry.EntryKind.EARNING)
                .orElseThrow(() -> new IllegalStateException("订单缺少课酬条目，需运营核对后退款"));
        if (settlementRepository.findByReversesEntryId(earning.getId()).isPresent()) return;
        SettlementEntry reversal = settlementRepository.saveAndFlush(SettlementEntry.reversalOf(earning));
        auditService.record("settlement:refund-reversal", "SUCCESS", java.util.Map.of(
                "orderId", order.getId(), "earningId", earning.getId(),
                "reversalId", reversal.getId(), "netCents", reversal.getNetCents()), "全额退款课酬冲回");
    }

    @Transactional(readOnly = true)
    public String refundNote(OrderSnapshot order) {
        return settlementRepository.findByOrderIdAndEntryKind(order.getId(), SettlementEntry.EntryKind.EARNING)
                .map(e -> e.getStatus() == SettlementEntry.SettlementStatus.PENDING
                        ? "退款执行时追加教师课酬红冲条目，待结算净额相应扣减 ¥" + String.format("%.2f", e.getNetCents() / 100.0)
                        : "该课酬已结算或已提现，自动退款将被阻止，请运营先完成账务处置")
                .orElse("订单缺少课酬条目，自动退款将被阻止，请运营核对");
    }

    /**
     * 教师结算汇总 + 最近明细。
     * 汇总与明细是两个独立查询：金额基于<b>全部</b>符合状态的条目做 DB 端聚合
     * （此前曾误用明细分页的前 50 条求和，条目 >50 时待结算金额少算）；明细固定返回最近 50 条。
     * 状态语义（如实）：PENDING 计入 pendingNetCents，PAID_OUT 计入 paidOutNetCents，
     * SETTLED（已结算待提现）当前不计入任一展示字段——该状态当前不可达（无结算/提现执行入口），
     * PENDING includes both original earnings and negative reversal entries by algebraic sum.
     */
    @Transactional(readOnly = true)
    public TeacherSettlement summaryOf(UUID teacherId) {
        long pending = settlementRepository.sumNetCentsByTeacherIdAndStatus(teacherId,
                SettlementEntry.SettlementStatus.PENDING);
        long paidOut = settlementRepository.sumNetCentsByTeacherIdAndStatus(teacherId,
                SettlementEntry.SettlementStatus.PAID_OUT);
        var entries = settlementRepository.findByTeacherIdOrderByCreatedAtDesc(teacherId,
                org.springframework.data.domain.PageRequest.of(0, 50));
        return new TeacherSettlement(pending, paidOut,
                entries.stream().map(e -> new EntryView(e.getId(), e.getBookingId(), e.getGrossCents(),
                        e.getPlatformFeeCents(), e.getNetCents(), e.getStatus().name(), e.getSettledAt(),
                        e.getEntryKind().name(), e.getReversesEntryId())).toList());
    }

    public record TeacherSettlement(long pendingNetCents, long paidOutNetCents, java.util.List<EntryView> entries) {
    }

    public record EntryView(UUID entryId, UUID bookingId, long grossCents, long platformFeeCents,
                            long netCents, String status, java.time.Instant settledAt,
                            String entryKind, UUID reversesEntryId) {
    }
}
