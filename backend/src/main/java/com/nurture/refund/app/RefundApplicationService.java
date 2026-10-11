package com.nurture.refund.app;

import com.nurture.messaging.app.MessagingApplicationService;
import com.nurture.order.app.OrderApplicationService;
import com.nurture.order.app.OrderApplicationService.OrderSnapshot;
import com.nurture.parent.app.ParentApplicationService;
import com.nurture.refund.domain.RefundCase;
import com.nurture.refund.infra.RefundCaseRepository;
import com.nurture.shared.audit.AuditService;
import com.nurture.shared.security.CallerContext;
import com.nurture.shared.twophase.TwoPhaseService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 退款仲裁应用服务 — 退款计算、两阶段影响评估与差错退款仲裁流。
 */
@Service
public class RefundApplicationService {

    private final RefundCaseRepository refundRepository;
    private final OrderApplicationService orderService;
    private final ParentApplicationService parentService;
    private final TwoPhaseService twoPhaseService;
    private final AuditService auditService;
    private final MessagingApplicationService messagingService;
    private final com.nurture.settlement.app.SettlementApplicationService settlementService;

    public RefundApplicationService(RefundCaseRepository refundRepository,
                                    OrderApplicationService orderService,
                                    ParentApplicationService parentService,
                                    TwoPhaseService twoPhaseService,
                                    AuditService auditService,
                                    MessagingApplicationService messagingService,
                                    com.nurture.settlement.app.SettlementApplicationService settlementService) {
        this.refundRepository = refundRepository;
        this.orderService = orderService;
        this.parentService = parentService;
        this.twoPhaseService = twoPhaseService;
        this.auditService = auditService;
        this.messagingService = messagingService;
        this.settlementService = settlementService;
    }

    /** 退款案件视图（供 admin DTO 化返回）。 */
    public record RefundCaseView(java.util.UUID caseId, java.util.UUID orderId, java.util.UUID requestedBy,
                                 String reason, long computedAmountCents, int affectedLessons,
                                 String status, java.util.UUID arbiterId, String decisionNote) {
        static RefundCaseView from(RefundCase c) {
            return new RefundCaseView(c.getId(), c.getOrderId(), c.getRequestedBy(), c.getReason(),
                    c.getComputedAmountCents(), c.getAffectedLessons(), c.getStatus().name(),
                    c.getArbiterId(), c.getDecisionNote());
        }
    }

    /** 家长/学生发起退款申请（首笔评估金额 = 订单实付）。 */
    @Transactional
    public RefundCase request(UUID orderId, String reason) {
        OrderSnapshot order = orderService.lockForRefundSnapshot(orderId);
        var caller = CallerContext.require();
        if (!order.getPayerParentId().equals(caller.accountId()) && !order.getStudentId().equals(caller.accountId())) {
            throw new org.springframework.security.access.AccessDeniedException("仅付款人或课程学生本人可申请退款");
        }
        if (reason == null || reason.isBlank() || reason.length() > 512) {
            throw new IllegalArgumentException("退款原因须为 1–512 字");
        }
        if (refundRepository.existsByOrderId(orderId)) throw new IllegalStateException("该订单已存在退款申请，请查看处理进度");
        if (!"PAID".equals(order.getStatus())) {
            throw new IllegalStateException("仅已支付订单可申请退款");
        }
        RefundCase refundCase = new RefundCase(orderId, CallerContext.require().accountId(), reason,
                order.getAmountCents(), 1);
        return refundRepository.save(refundCase);
    }

    @Transactional
    public void refundForCancelledBooking(OrderSnapshot order,String reason){executeForBookingCancellation(order,reason);}

    /** 供 booking 域调用：取消已付课时时的自动全额退款闭环（原路入账家庭钱包）。 */
    @Transactional
    public RefundCase executeForBookingCancellation(OrderSnapshot order, String reason) {
        order = orderService.lockForRefundSnapshot(order.getId());
        if (!"PAID".equals(order.getStatus())) throw new IllegalStateException("订单已处理，请刷新课程和退款进度");
        if (refundRepository.existsByOrderId(order.getId())) throw new IllegalStateException("订单已有退款申请，请联系运营处理课程取消");
        settlementService.reverseForRefund(order);
        RefundCase refundCase = new RefundCase(order.getId(), order.getPayerParentId(), reason,
                order.getAmountCents(), 1);
        refundCase.approve(order.getPayerParentId(), "取消课时自动全额退款");
        parentService.creditRefund(order.getPayerParentId(), order.getId(),
                order.getAmountCents(), "取消课时退款（原路入账）");
        refundCase.markExecuted();
        messagingService.notify(order.getPayerParentId(), "REFUND",
                "退款已入账", "订单 " + order.getOrderNo() + " 因取消课时已全额退回家庭钱包。");
        auditService.record("refund:booking-cancel", "SUCCESS",
                java.util.Map.of("orderId", order.getId().toString(),
                        "amountCents", order.getAmountCents()), "取消课时自动退款");
        return refundRepository.save(refundCase);
    }

    /** 仲裁 Preview：预计算退款金额、影响课时与钱包入账，签发短效确认凭证。 */
    @Transactional
    public ArbitrationPreview previewArbitration(UUID refundCaseId) {
        RefundCase refundCase = refundRepository.lockById(refundCaseId)
                .orElseThrow(() -> new IllegalArgumentException("退款案例不存在"));
        if (refundCase.getStatus() != RefundCase.RefundStatus.REQUESTED && refundCase.getStatus() != RefundCase.RefundStatus.EVALUATED) {
            throw new IllegalStateException("退款已处理，不能重新评估");
        }
        OrderSnapshot order = orderService.getSnapshot(refundCase.getOrderId());

        Map<String, Object> impact = new LinkedHashMap<>();
        impact.put("refundCaseId", refundCaseId);
        impact.put("orderNo", order.getOrderNo());
        impact.put("refundAmountCents", refundCase.getComputedAmountCents());
        impact.put("refundAmountYuan", String.format("%.2f", refundCase.getComputedAmountCents() / 100.0));
        impact.put("affectedLessons", refundCase.getAffectedLessons());
        impact.put("walletCreditTarget", "家庭统一教育支付钱包（原路入账）");
        impact.put("orderAfterRefund", "已支付订单将关闭");
        // Preview describes the actual reversal behavior without mutating accounting entries.
        impact.put("settlementNote", settlementService.refundNote(order));
        impact.put("reason", refundCase.getReason());

        var grant = twoPhaseService.issue("refund:arbitrate", refundCaseId, impact.toString());
        if (refundCase.getStatus() == RefundCase.RefundStatus.REQUESTED) refundCase.evaluate(refundCase.getComputedAmountCents(), refundCase.getAffectedLessons());
        return new ArbitrationPreview(impact, grant.confirmationToken(), grant.expiresAt());
    }

    /** 仲裁 Execute：携带确认凭证原子执行批准 + 钱包入账 + 订单关闭 + 审计。 */
    @Transactional
    public RefundCase executeArbitration(UUID refundCaseId, boolean approve, String decisionNote,
                                         String confirmationToken) {
        if (decisionNote != null && decisionNote.length() > 512) throw new IllegalArgumentException("处理说明不能超过 512 字");
        RefundCase refundCase = refundRepository.lockById(refundCaseId)
                .orElseThrow(() -> new IllegalArgumentException("退款案例不存在"));
        UUID arbiterId = CallerContext.require().accountId();

        if (refundCase.getStatus() != RefundCase.RefundStatus.REQUESTED
                && refundCase.getStatus() != RefundCase.RefundStatus.EVALUATED) {
            throw new IllegalStateException("退款已处理，不可重复执行或驳回");
        }

        if (approve) {
            twoPhaseService.consume("refund:arbitrate", refundCaseId, confirmationToken);
            refundCase.approve(arbiterId, decisionNote);
            OrderSnapshot order = orderService.lockForRefundSnapshot(refundCase.getOrderId());
            if (!"PAID".equals(order.getStatus())) throw new IllegalStateException("订单已非已支付状态，请核对退款结果");
            settlementService.reverseForRefund(order);
            parentService.creditRefund(order.getPayerParentId(), order.getId(),
                    refundCase.getComputedAmountCents(), "差错退款入账（仲裁通过）");
            orderService.closeIfPresent(order.getBookingId());
            refundCase.markExecuted();
            messagingService.notify(order.getPayerParentId(), "REFUND",
                    "退款已入账", "订单 " + order.getOrderNo() + " 的退款已原路退回家庭钱包，请注意查收。");
            auditService.record("refund:arbitrate-execute", "SUCCESS",
                    Map.of("refundCaseId", refundCaseId, "amountCents", refundCase.getComputedAmountCents()),
                    "退款仲裁执行通过，退款已入账");
        } else {
            refundCase.reject(arbiterId, decisionNote);
            auditService.record("refund:arbitrate-execute", "REJECTED",
                    Map.of("refundCaseId", refundCaseId, "note", decisionNote), "退款仲裁驳回");
        }
        return refundCase;
    }

    @Transactional(readOnly = true)
    public Page<RefundCase> listAll(Pageable pageable) {
        return refundRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Page<RefundCase> listByRequester(UUID requesterId, Pageable pageable) {
        return refundRepository.findByRequestedByOrderByCreatedAtDesc(requesterId, pageable);
    }

    @Transactional(readOnly = true)
    public RefundCase get(UUID refundCaseId) {
        return refundRepository.findById(refundCaseId)
                .orElseThrow(() -> new IllegalArgumentException("退款案例不存在"));
    }

    public record ArbitrationPreview(Map<String, Object> cascadeImpact, String confirmationToken,
                                     java.time.Instant confirmationTokenExpiresAt) {
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<RefundCaseView> listAllViews(org.springframework.data.domain.Pageable pageable) {
        return refundRepository.findAll(pageable).map(RefundCaseView::from);
    }
    @Transactional(readOnly=true)
    public org.springframework.data.domain.Page<RefundCaseView> listAllViews(String status,org.springframework.data.domain.Pageable pageable) {
        return status==null || status.isBlank()?listAllViews(pageable):refundRepository.findByStatus(RefundCase.RefundStatus.valueOf(status),pageable).map(RefundCaseView::from);
    }

    @Transactional
    public RefundCaseView executeArbitrationView(java.util.UUID caseId, boolean approve,
                                                 String decisionNote, String confirmationToken) {
        return RefundCaseView.from(executeArbitration(caseId, approve, decisionNote, confirmationToken));
    }
}
