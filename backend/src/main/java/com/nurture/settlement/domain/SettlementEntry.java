package com.nurture.settlement.domain;

import com.nurture.shared.domain.AggregateRoot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * 教师课酬结算条目 — 课酬核算、平台抽成与提现对账。
 */
@Entity
@Table(name = "settlement_entries", indexes = {
        @Index(name = "ix_settlement_teacher", columnList = "teacherId, status"),
        @Index(name = "ux_settlement_booking", columnList = "bookingId", unique = true)
})
public class SettlementEntry extends AggregateRoot {

    public enum SettlementStatus { PENDING, SETTLED, PAID_OUT }

    @Column(name = "teacher_id", nullable = false)
    private UUID teacherId;

    @Column(name = "booking_id", nullable = false)
    private UUID bookingId;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "gross_cents", nullable = false)
    private long grossCents;

    @Column(name = "platform_fee_cents", nullable = false)
    private long platformFeeCents;

    @Column(name = "net_cents", nullable = false)
    private long netCents;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private SettlementStatus status = SettlementStatus.PENDING;

    @Column(name = "settled_at")
    private Instant settledAt;

    protected SettlementEntry() {
    }

    public SettlementEntry(UUID teacherId, UUID bookingId, UUID orderId, long grossCents, long platformFeeCents) {
        this.teacherId = teacherId;
        this.bookingId = bookingId;
        this.orderId = orderId;
        this.grossCents = grossCents;
        this.platformFeeCents = platformFeeCents;
        this.netCents = grossCents - platformFeeCents;
    }

    public void markSettled() {
        if (status != SettlementStatus.PENDING) {
            throw new IllegalStateException("仅待结算条目可标记为已结算");
        }
        this.status = SettlementStatus.SETTLED;
        this.settledAt = Instant.now();
    }

    public void markPaidOut() {
        if (status != SettlementStatus.SETTLED) {
            throw new IllegalStateException("仅已结算条目可提现");
        }
        this.status = SettlementStatus.PAID_OUT;
    }

    public UUID getTeacherId() {
        return teacherId;
    }

    public UUID getBookingId() {
        return bookingId;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public long getGrossCents() {
        return grossCents;
    }

    public long getPlatformFeeCents() {
        return platformFeeCents;
    }

    public long getNetCents() {
        return netCents;
    }

    public SettlementStatus getStatus() {
        return status;
    }

    public Instant getSettledAt() {
        return settledAt;
    }
}
