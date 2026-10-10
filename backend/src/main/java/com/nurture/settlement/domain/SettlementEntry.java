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
        @Index(name = "ix_settlement_teacher", columnList = "teacherId, status")
})
public class SettlementEntry extends AggregateRoot {

    public enum SettlementStatus { PENDING, SETTLED, PAID_OUT }
    public enum EntryKind { EARNING, REVERSAL }

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_kind", nullable = false, length = 16)
    private EntryKind entryKind = EntryKind.EARNING;

    @Column(name = "reverses_entry_id")
    private UUID reversesEntryId;

    public static SettlementEntry reversalOf(SettlementEntry earning) {
        if (earning.entryKind != EntryKind.EARNING || earning.status != SettlementStatus.PENDING) {
            throw new IllegalStateException("仅待结算收益可自动冲回，已结算或已提现请联系运营处理");
        }
        SettlementEntry reversal = new SettlementEntry(earning.teacherId, earning.bookingId,
                earning.orderId, Math.negateExact(earning.grossCents), 0);
        reversal.netCents = Math.negateExact(earning.netCents);
        reversal.entryKind = EntryKind.REVERSAL;
        reversal.reversesEntryId = earning.getId();
        return reversal;
    }

    public EntryKind getEntryKind() { return entryKind; }
    public UUID getReversesEntryId() { return reversesEntryId; }

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
