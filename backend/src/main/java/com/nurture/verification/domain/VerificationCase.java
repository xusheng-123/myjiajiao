package com.nurture.verification.domain;

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
 * 教师资质审核案例 — 审核状态机：SUBMITTED → UNDER_REVIEW → APPROVED / REJECTED。
 */
@Entity
@Table(name = "verification_cases",
        indexes = {
                @Index(name = "ix_verification_teacher", columnList = "teacherId"),
                @Index(name = "ix_verification_status", columnList = "status")
        })
public class VerificationCase extends AggregateRoot {

    public enum VerificationStatus { SUBMITTED, UNDER_REVIEW, APPROVED, REJECTED }

    @Column(name = "teacher_id", nullable = false)
    private UUID teacherId;

    @Column(name = "id_document_key", length = 256)
    private String idDocumentKey;

    @Column(name = "degree_cert_key", length = 256)
    private String degreeCertKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private VerificationStatus status = VerificationStatus.SUBMITTED;

    @Column(name = "reviewer_id")
    private UUID reviewerId;

    @Column(name = "decision_note", length = 512)
    private String decisionNote;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt = Instant.now();

    @Column(name = "decided_at")
    private Instant decidedAt;

    protected VerificationCase() {
    }

    public VerificationCase(UUID teacherId, String idDocumentKey, String degreeCertKey) {
        this.teacherId = teacherId;
        this.idDocumentKey = idDocumentKey;
        this.degreeCertKey = degreeCertKey;
    }

    /** 状态机迁移校验 — 非法迁移一律拒绝。 */
    public void startReview() {
        if (status != VerificationStatus.SUBMITTED) {
            throw new IllegalStateException("仅处于「已提交」状态的资质可以进入审核");
        }
        this.status = VerificationStatus.UNDER_REVIEW;
    }

    public void approve(UUID reviewerId, String note) {
        // 运营作出决定即视为完成审核：允许从「已提交」直接推进，避免审核流程断链
        if (status == VerificationStatus.SUBMITTED) {
            startReview();
        }
        if (status != VerificationStatus.UNDER_REVIEW) {
            throw new IllegalStateException("仅处于「审核中」状态的资质可以批准");
        }
        this.status = VerificationStatus.APPROVED;
        this.reviewerId = reviewerId;
        this.decisionNote = note;
        this.decidedAt = Instant.now();
    }

    public void reject(UUID reviewerId, String note) {
        if (status == VerificationStatus.SUBMITTED) {
            startReview();
        }
        if (status != VerificationStatus.UNDER_REVIEW) {
            throw new IllegalStateException("仅处于「审核中」状态的资质可以驳回");
        }
        if (note == null || note.isBlank()) {
            throw new IllegalArgumentException("驳回必须附上明确原因，便于导师补充材料");
        }
        this.status = VerificationStatus.REJECTED;
        this.reviewerId = reviewerId;
        this.decisionNote = note;
        this.decidedAt = Instant.now();
    }

    public UUID getTeacherId() {
        return teacherId;
    }

    public String getIdDocumentKey() {
        return idDocumentKey;
    }

    public String getDegreeCertKey() {
        return degreeCertKey;
    }

    public VerificationStatus getStatus() {
        return status;
    }

    public UUID getReviewerId() {
        return reviewerId;
    }

    public String getDecisionNote() {
        return decisionNote;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }
}
