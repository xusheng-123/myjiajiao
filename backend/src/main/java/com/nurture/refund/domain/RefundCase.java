package com.nurture.refund.domain;

import com.nurture.shared.domain.AggregateRoot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * 退款仲裁案例 — 退款计算、两阶段影响评估与差错仲裁流。
 * 状态机：REQUESTED → EVALUATED → APPROVED → EXECUTED；或 REJECTED 终态。
 */
@Entity
@Table(name = "refund_cases", indexes = {
        @Index(name = "ix_refund_order", columnList = "orderId"),
        @Index(name = "ix_refund_status", columnList = "status")
})
public class RefundCase extends AggregateRoot {

    public enum RefundStatus { REQUESTED, EVALUATED, APPROVED, REJECTED, EXECUTED }

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "requested_by", nullable = false)
    private UUID requestedBy;

    @Column(name = "reason", nullable = false, length = 512)
    private String reason;

    @Column(name = "computed_amount_cents", nullable = false)
    private long computedAmountCents;

    @Column(name = "affected_lessons")
    private int affectedLessons;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private RefundStatus status = RefundStatus.REQUESTED;

    @Column(name = "arbiter_id")
    private UUID arbiterId;

    @Column(name = "decision_note", length = 512)
    private String decisionNote;

    protected RefundCase() {
    }

    public RefundCase(UUID orderId, UUID requestedBy, String reason, long computedAmountCents, int affectedLessons) {
        this.orderId = orderId;
        this.requestedBy = requestedBy;
        this.reason = reason;
        this.computedAmountCents = computedAmountCents;
        this.affectedLessons = affectedLessons;
    }

    public void evaluate(long amountCents, int affectedLessons) {
        if (status != RefundStatus.REQUESTED) {
            throw new IllegalStateException("仅待评估的退款案例可以更新评估结果");
        }
        this.computedAmountCents = amountCents;
        this.affectedLessons = affectedLessons;
        this.status = RefundStatus.EVALUATED;
    }

    public void approve(UUID arbiterId, String note) {
        if (status != RefundStatus.EVALUATED && status != RefundStatus.REQUESTED) {
            throw new IllegalStateException("当前退款状态不可批准: " + status);
        }
        this.status = RefundStatus.APPROVED;
        this.arbiterId = arbiterId;
        this.decisionNote = note;
    }

    public void reject(UUID arbiterId, String note) {
        if (note == null || note.isBlank()) {
            throw new IllegalArgumentException("驳回退款必须附上明确理由");
        }
        this.status = RefundStatus.REJECTED;
        this.arbiterId = arbiterId;
        this.decisionNote = note;
    }

    public void markExecuted() {
        if (status != RefundStatus.APPROVED) {
            throw new IllegalStateException("仅已批准的退款可以执行");
        }
        this.status = RefundStatus.EXECUTED;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public UUID getRequestedBy() {
        return requestedBy;
    }

    public String getReason() {
        return reason;
    }

    public long getComputedAmountCents() {
        return computedAmountCents;
    }

    public int getAffectedLessons() {
        return affectedLessons;
    }

    public RefundStatus getStatus() {
        return status;
    }

    public UUID getArbiterId() {
        return arbiterId;
    }

    public String getDecisionNote() {
        return decisionNote;
    }
}
