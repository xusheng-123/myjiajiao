package com.nurture.booking.domain;

import com.nurture.shared.domain.AggregateRoot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 课程临近提醒登记（D12）——同一预约同一档期仅提醒一次（唯一约束去重）。
 * 提醒内容经站内通知（system_notices）触达双方；「已处理状态」复用通知已读。
 */
@Entity
@Table(name = "lesson_reminders",
        indexes = {
                @Index(name = "ux_lesson_reminders_booking_slot", columnList = "bookingId, slotId", unique = true),
                @Index(name = "ix_lesson_reminders_start", columnList = "lessonStartAt")
        })
public class LessonReminder extends AggregateRoot {

    @Column(name = "booking_id", nullable = false)
    private UUID bookingId;

    @Column(name = "slot_id", nullable = false)
    private UUID slotId;

    @Column(name = "lesson_start_at", nullable = false)
    private OffsetDateTime lessonStartAt;

    @Column(name = "reminded_at", nullable = false)
    private Instant remindedAt;

    protected LessonReminder() {
    }

    public LessonReminder(UUID bookingId, UUID slotId, OffsetDateTime lessonStartAt) {
        this.bookingId = bookingId;
        this.slotId = slotId;
        this.lessonStartAt = lessonStartAt;
        this.remindedAt = Instant.now();
    }

    public UUID getBookingId() {
        return bookingId;
    }

    public UUID getSlotId() {
        return slotId;
    }

    public OffsetDateTime getLessonStartAt() {
        return lessonStartAt;
    }

    public Instant getRemindedAt() {
        return remindedAt;
    }
}
