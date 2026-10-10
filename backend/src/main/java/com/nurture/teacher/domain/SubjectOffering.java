package com.nurture.teacher.domain;

import com.nurture.shared.domain.AggregateRoot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * 擅长科目开课条目 — 撮合检索与课时定价的事实源。
 */
@Entity
@Table(name = "subject_offerings",
        indexes = {
                @Index(name = "ix_offering_teacher", columnList = "teacherId"),
                @Index(name = "ix_offering_subject", columnList = "subject, gradeBand")
        })
public class SubjectOffering extends AggregateRoot {

    @Column(name = "teacher_id", nullable = false)
    private UUID teacherId;

    @Column(name = "subject", nullable = false, length = 32)
    private String subject;

    @Column(name = "grade_band", nullable = false, length = 32)
    private String gradeBand;

    @Column(name = "price_per_hour_cents", nullable = false)
    private long pricePerHourCents;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    protected SubjectOffering() {
    }

    public SubjectOffering(UUID teacherId, String subject, String gradeBand, long pricePerHourCents) {
        if (pricePerHourCents <= 0) {
            // 与调价路径（reprice）同一约束：新增课程入口同样不得接受 0 或负数定价
            throw new IllegalArgumentException("课时定价必须为正数（单位：分）");
        }
        this.teacherId = teacherId;
        this.subject = subject;
        this.gradeBand = gradeBand;
        this.pricePerHourCents = pricePerHourCents;
    }

    public void reprice(long newPricePerHourCents) {
        if (newPricePerHourCents <= 0) {
            throw new IllegalArgumentException("课时定价必须为正数（单位：分）");
        }
        this.pricePerHourCents = newPricePerHourCents;
    }

    public void deactivate() {
        this.active = false;
    }

    public UUID getTeacherId() {
        return teacherId;
    }

    public String getSubject() {
        return subject;
    }

    public String getGradeBand() {
        return gradeBand;
    }

    public long getPricePerHourCents() {
        return pricePerHourCents;
    }

    public boolean isActive() {
        return active;
    }
}
