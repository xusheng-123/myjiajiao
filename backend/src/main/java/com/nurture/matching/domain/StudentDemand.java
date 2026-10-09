package com.nurture.matching.domain;

import com.nurture.shared.domain.AggregateRoot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * 学生需求发布 — 「告诉平台我的需求」入口：与筛选检索并存，不强迫用户先走问卷。
 */
@Entity
@Table(name = "student_demands", indexes = {
        @Index(name = "ix_demand_student", columnList = "studentId, createdAt"),
        @Index(name = "ix_demand_open", columnList = "status, createdAt")
})
public class StudentDemand extends AggregateRoot {

    public enum DemandStatus { OPEN, MATCHED, CLOSED }

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "subject", nullable = false, length = 32)
    private String subject;

    @Column(name = "grade_band", length = 32)
    private String gradeBand;

    @Column(name = "note", length = 1024)
    private String note;

    @Column(name = "budget_max_cents")
    private Long budgetMaxCents;

    @Column(name = "preferred_time", length = 128)
    private String preferredTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private DemandStatus status = DemandStatus.OPEN;

    protected StudentDemand() {
    }

    public StudentDemand(UUID studentId, String subject, String gradeBand, String note,
                         Long budgetMaxCents, String preferredTime) {
        this.studentId = studentId;
        this.subject = subject;
        this.gradeBand = gradeBand;
        this.note = note;
        this.budgetMaxCents = budgetMaxCents;
        this.preferredTime = preferredTime;
    }

    public void markMatched() {
        if (status != DemandStatus.OPEN) {
            throw new IllegalStateException("仅开放中的需求可以标记为已撮合");
        }
        this.status = DemandStatus.MATCHED;
    }

    public void close() {
        this.status = DemandStatus.CLOSED;
    }

    public UUID getStudentId() {
        return studentId;
    }

    public String getSubject() {
        return subject;
    }

    public String getGradeBand() {
        return gradeBand;
    }

    public String getNote() {
        return note;
    }

    public Long getBudgetMaxCents() {
        return budgetMaxCents;
    }

    public String getPreferredTime() {
        return preferredTime;
    }

    public DemandStatus getStatus() {
        return status;
    }
}
