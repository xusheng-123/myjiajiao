package com.nurture.classroom.domain;

import com.nurture.shared.domain.AggregateRoot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * 入会前准备任务 — ADHD 友好任务切块（Task Chunking）：原子微任务，支持无序完成或跳过。
 */
@Entity
@Table(name = "preparation_tasks",
        indexes = {
                @Index(name = "ix_prep_task_booking", columnList = "bookingId, sortIndex")
        })
public class PreparationTask extends AggregateRoot {

    @Column(name = "booking_id", nullable = false)
    private UUID bookingId;

    @Column(name = "title", nullable = false, length = 64)
    private String title;

    @Column(name = "sort_index", nullable = false)
    private int sortIndex;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected PreparationTask() {
    }

    public PreparationTask(UUID bookingId, String title, int sortIndex) {
        this.bookingId = bookingId;
        this.title = title;
        this.sortIndex = sortIndex;
    }

    /** 无序勾选：任务间不阻塞。 */
    public void complete() {
        this.completedAt = Instant.now();
    }

    public void reset() {
        this.completedAt = null;
    }

    public UUID getBookingId() {
        return bookingId;
    }

    public String getTitle() {
        return title;
    }

    public int getSortIndex() {
        return sortIndex;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
