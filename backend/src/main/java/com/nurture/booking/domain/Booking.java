package com.nurture.booking.domain;

import com.nurture.shared.domain.AggregateRoot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.util.Set;
import java.util.UUID;

/**
 * 预约单生命周期状态机：REQUESTED → CONFIRMED → IN_PROGRESS → COMPLETED；
 * 任意非终态可 → CANCELLED（两阶段执行）；COMPLETED/CONFIRMED 结算后 → CLOSED。
 */
@Entity
@Table(name = "bookings", indexes = {
        @Index(name = "ix_booking_student", columnList = "studentId, createdAt"),
        @Index(name = "ix_booking_teacher", columnList = "teacherId, createdAt"),
        @Index(name = "ux_booking_slot", columnList = "slotId", unique = true)
})
public class Booking extends AggregateRoot {

    public enum BookingType { TRIAL, FORMAL }

    public enum BookingStatus { REQUESTED, CONFIRMED, IN_PROGRESS, COMPLETED, CANCELLED, CLOSED }

    private static final Set<BookingStatus> ACTIVE_STATUSES =
            Set.of(BookingStatus.REQUESTED, BookingStatus.CONFIRMED, BookingStatus.IN_PROGRESS);

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "teacher_id", nullable = false)
    private UUID teacherId;

    @Column(name = "slot_id", nullable = false)
    private UUID slotId;

    @Column(name = "subject", nullable = false, length = 32)
    private String subject;

    @Enumerated(EnumType.STRING)
    @Column(name = "booking_type", nullable = false, length = 16)
    private BookingType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private BookingStatus status = BookingStatus.REQUESTED;

    @Column(name = "price_cents", nullable = false)
    private long priceCents;

    @Column(name = "cancel_reason", length = 512)
    private String cancelReason;

    protected Booking() {
    }

    public Booking(UUID studentId, UUID teacherId, UUID slotId, String subject,
                   BookingType type, long priceCents) {
        this.studentId = studentId;
        this.teacherId = teacherId;
        this.slotId = slotId;
        this.subject = subject;
        this.type = type;
        this.priceCents = priceCents;
    }

    public void confirm() {
        requireTransition(BookingStatus.CONFIRMED);
        this.status = BookingStatus.CONFIRMED;
    }

    public void start() {
        requireTransition(BookingStatus.IN_PROGRESS);
        this.status = BookingStatus.IN_PROGRESS;
    }

    public void complete() {
        requireTransition(BookingStatus.COMPLETED);
        this.status = BookingStatus.COMPLETED;
    }

    public void cancel(String reason) {
        if (!ACTIVE_STATUSES.contains(status)) {
            throw new IllegalStateException("当前状态不可取消: " + status);
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("取消必须说明原因，便于双向反馈与仲裁");
        }
        this.status = BookingStatus.CANCELLED;
        this.cancelReason = reason;
    }

    public void close() {
        if (status != BookingStatus.COMPLETED && status != BookingStatus.CANCELLED) {
            throw new IllegalStateException("仅已完成或已取消的预约可关闭");
        }
        this.status = BookingStatus.CLOSED;
    }

    /** 改期（D11）：仅替换档期引用；订单与评价关系保持不变。 */
    public void rescheduleTo(UUID newSlotId) {
        this.slotId = newSlotId;
    }

    public boolean cancellable() {
        return ACTIVE_STATUSES.contains(status);
    }

    private void requireTransition(BookingStatus target) {
        boolean valid = switch (status) {
            case REQUESTED -> target == BookingStatus.CONFIRMED;
            case CONFIRMED -> target == BookingStatus.IN_PROGRESS;
            case IN_PROGRESS -> target == BookingStatus.COMPLETED;
            default -> false;
        };
        if (!valid) {
            throw new IllegalStateException("非法状态迁移: " + status + " → " + target);
        }
    }

    public UUID getStudentId() {
        return studentId;
    }

    public UUID getTeacherId() {
        return teacherId;
    }

    public UUID getSlotId() {
        return slotId;
    }

    public String getSubject() {
        return subject;
    }

    public BookingType getType() {
        return type;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public long getPriceCents() {
        return priceCents;
    }

    public String getCancelReason() {
        return cancelReason;
    }
}
