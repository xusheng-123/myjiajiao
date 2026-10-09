package com.nurture.settlement.app;

import com.nurture.order.domain.Order;
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

    public SettlementApplicationService(SettlementEntryRepository settlementRepository,
                                        PlatformConfigReader configReader) {
        this.settlementRepository = settlementRepository;
        this.configReader = configReader;
    }

    /** 供支付域在订单支付成功后调用：按平台抽成生成课酬结算条目。 */
    @Transactional
    public SettlementEntry createEntryForPaidOrder(Order order) {
        BigDecimal feeRate = configReader.decimalValue("settlement.platform-fee-rate", BigDecimal.valueOf(0.15));
        long fee = BigDecimal.valueOf(order.getAmountCents())
                .multiply(feeRate)
                .setScale(0, java.math.RoundingMode.HALF_UP)
                .longValueExact();
        return settlementRepository.save(new SettlementEntry(order.getTeacherId(), order.getBookingId(),
                order.getId(), order.getAmountCents(), fee));
    }

    @Transactional(readOnly = true)
    public TeacherSettlement summaryOf(UUID teacherId) {
        var entries = settlementRepository.findByTeacherIdOrderByCreatedAtDesc(teacherId,
                org.springframework.data.domain.PageRequest.of(0, 50));
        long pending = 0;
        long paidOut = 0;
        for (var e : entries) {
            if (e.getStatus() == SettlementEntry.SettlementStatus.PENDING) {
                pending += e.getNetCents();
            } else if (e.getStatus() == SettlementEntry.SettlementStatus.PAID_OUT) {
                paidOut += e.getNetCents();
            }
        }
        return new TeacherSettlement(pending, paidOut,
                entries.stream().map(e -> new EntryView(e.getId(), e.getBookingId(), e.getGrossCents(),
                        e.getPlatformFeeCents(), e.getNetCents(), e.getStatus().name(), e.getSettledAt())).toList());
    }

    public record TeacherSettlement(long pendingNetCents, long paidOutNetCents, java.util.List<EntryView> entries) {
    }

    public record EntryView(UUID entryId, UUID bookingId, long grossCents, long platformFeeCents,
                            long netCents, String status, java.time.Instant settledAt) {
    }
}
