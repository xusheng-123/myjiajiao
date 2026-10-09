package com.nurture.parent.domain;

import com.nurture.shared.domain.AggregateRoot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * 监护绑定 — 家长与学生两个独立实体之间的解耦关联；一个学生可绑定多位监护人。
 */
@Entity
@Table(name = "guardian_bindings",
        indexes = {
                @Index(name = "ux_guardian_pair", columnList = "parentId, studentId", unique = true),
                @Index(name = "ix_guardian_student", columnList = "studentId")
        })
public class GuardianBinding extends AggregateRoot {

    public enum BindingStatus { PENDING, ACTIVE, REVOKED }

    @Column(name = "parent_id", nullable = false)
    private UUID parentId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private BindingStatus status = BindingStatus.PENDING;

    @Column(name = "relation", length = 16)
    private String relation;

    protected GuardianBinding() {
    }

    public GuardianBinding(UUID parentId, UUID studentId, String relation) {
        this.parentId = parentId;
        this.studentId = studentId;
        this.relation = relation;
    }

    public void activate() {
        this.status = BindingStatus.ACTIVE;
    }

    public void revoke() {
        this.status = BindingStatus.REVOKED;
    }

    public UUID getParentId() {
        return parentId;
    }

    public UUID getStudentId() {
        return studentId;
    }

    public BindingStatus getStatus() {
        return status;
    }

    public String getRelation() {
        return relation;
    }
}
