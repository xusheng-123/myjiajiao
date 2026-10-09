package com.nurture.scheduling.domain;

import com.nurture.shared.domain.AggregateRoot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 教师可用时段 — 并发冲突排他检测依赖数据库唯一约束 + 乐观锁。
 */
@Entity
@Table(name = "availability_slots",
        indexes = {
                @Index(name = "ix_slot_teacher_time", columnList = "teacherId, startAt"),
                @Index(name = "ux_slot_teacher_time", columnList = "teacherId, startAt", unique = true)
        })
public class AvailabilitySlot extends AggregateRoot {

    public enum SlotStatus { OPEN, HELD, BOOKED, BLOCKED }

    @Column(name = "teacher_id", nullable = false)
    private UUID teacherId;

    @Column(name = "start_at", nullable = false)
    private OffsetDateTime startAt;

    @Column(name = "end_at", nullable = false)
    private OffsetDateTime endAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private SlotStatus status = SlotStatus.OPEN;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected AvailabilitySlot() {
    }

    public AvailabilitySlot(UUID teacherId, OffsetDateTime startAt, OffsetDateTime endAt) {
        if (!endAt.isAfter(startAt)) {
            throw new IllegalArgumentException("时段结束时间必须晚于开始时间");
        }
        this.teacherId = teacherId;
        this.startAt = startAt;
        this.endAt = endAt;
    }

    public void hold() {
        requireOpen();
        this.status = SlotStatus.HELD;
    }

    public void book() {
        if (status != SlotStatus.OPEN && status != SlotStatus.HELD) {
            throw new IllegalStateException("该时段已被占用，无法预约");
        }
        this.status = SlotStatus.BOOKED;
    }

    public void release() {
        if (status != SlotStatus.HELD && status != SlotStatus.BOOKED) {
            throw new IllegalStateException("该时段不在占用状态，无法释放");
        }
        this.status = SlotStatus.OPEN;
    }

    public void block() {
        requireOpen();
        this.status = SlotStatus.BLOCKED;
    }

    private void requireOpen() {
        if (status != SlotStatus.OPEN) {
            throw new IllegalStateException("仅空闲（OPEN）时段可以变更，当前状态: " + status);
        }
    }

    public UUID getTeacherId() {
        return teacherId;
    }

    public OffsetDateTime getStartAt() {
        return startAt;
    }

    public OffsetDateTime getEndAt() {
        return endAt;
    }

    public SlotStatus getStatus() {
        return status;
    }
}
