package com.nurture.order.domain;

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
 * 课时交易订单 — 订单状态机（待付 → 已付 / 已取消 → 已关闭）与对账快照。
 */
@Entity
@Table(name = "orders", indexes = {
        @Index(name = "ux_orders_order_no", columnList = "orderNo", unique = true),
        @Index(name = "ix_orders_booking", columnList = "bookingId"),
        @Index(name = "ix_orders_payer", columnList = "payerParentId, createdAt")
})
public class Order extends AggregateRoot {

    public enum OrderStatus { PENDING_PAYMENT, PAID, CANCELLED, CLOSED }

    public enum OrderType { TRIAL_LESSON, FORMAL_LESSONS }

    @Column(name = "order_no", nullable = false, length = 32)
    private String orderNo;

    @Column(name = "booking_id", nullable = false)
    private UUID bookingId;

    @Column(name = "payer_parent_id", nullable = false)
    private UUID payerParentId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "teacher_id", nullable = false)
    private UUID teacherId;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", nullable = false, length = 24)
    private OrderType type;

    @Column(name = "amount_cents", nullable = false)
    private long amountCents;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private OrderStatus status = OrderStatus.PENDING_PAYMENT;

    /** 对账快照：下单时刻的价格、科目、时段等关键事实。 */
    @Column(name = "reconciliation_snapshot", columnDefinition = "text")
    private String reconciliationSnapshot;

    @Column(name = "paid_at")
    private Instant paidAt;

    protected Order() {
    }

    public Order(String orderNo, UUID bookingId, UUID payerParentId, UUID studentId, UUID teacherId,
                 OrderType type, long amountCents, String reconciliationSnapshot) {
        if (amountCents <= 0) {
            throw new IllegalArgumentException("订单金额必须为正数（单位：分）");
        }
        this.orderNo = orderNo;
        this.bookingId = bookingId;
        this.payerParentId = payerParentId;
        this.studentId = studentId;
        this.teacherId = teacherId;
        this.type = type;
        this.amountCents = amountCents;
        this.reconciliationSnapshot = reconciliationSnapshot;
    }

    public void markPaid() {
        if (status != OrderStatus.PENDING_PAYMENT) {
            throw new IllegalStateException("仅待支付订单可标记为已付");
        }
        this.status = OrderStatus.PAID;
        this.paidAt = Instant.now();
    }

    public void cancel() {
        if (status != OrderStatus.PENDING_PAYMENT) {
            throw new IllegalStateException("仅待支付订单可取消");
        }
        this.status = OrderStatus.CANCELLED;
    }

    public void close() {
        if (status != OrderStatus.PAID && status != OrderStatus.CANCELLED) {
            throw new IllegalStateException("仅已付/已取消订单可关闭");
        }
        this.status = OrderStatus.CLOSED;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public UUID getBookingId() {
        return bookingId;
    }

    public UUID getPayerParentId() {
        return payerParentId;
    }

    public UUID getStudentId() {
        return studentId;
    }

    public UUID getTeacherId() {
        return teacherId;
    }

    public OrderType getType() {
        return type;
    }

    public long getAmountCents() {
        return amountCents;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public String getReconciliationSnapshot() {
        return reconciliationSnapshot;
    }

    public Instant getPaidAt() {
        return paidAt;
    }
}
