package com.nurture.learning.domain;

import com.nurture.shared.domain.AggregateRoot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** 课后任务 / 微作业 — 教师针对某节已确认课程发布；学生提交后教师反馈即完成。 */
@Entity
@Table(name = "learning_tasks", indexes = {
        @Index(name = "ix_ltask_student", columnList = "studentId, status"),
        @Index(name = "ix_ltask_teacher", columnList = "teacherId"),
        @Index(name = "ix_ltask_booking", columnList = "bookingId")
})
public class LearningTask extends AggregateRoot {

    public enum Status { PENDING, SUBMITTED, COMPLETED }
    public enum Priority { LOW, NORMAL, HIGH }

    @Column(name = "booking_id", nullable = false)
    private UUID bookingId;

    @Column(name = "teacher_id", nullable = false)
    private UUID teacherId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "title", nullable = false, length = 120)
    private String title;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 12)
    private Priority priority = Priority.NORMAL;

    @Column(name = "deadline")
    private Instant deadline;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private Status status = Status.PENDING;

    @Column(name = "submission_content", columnDefinition = "text")
    private String submissionContent;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "feedback", columnDefinition = "text")
    private String feedback;

    @Column(name = "feedback_at")
    private Instant feedbackAt;

    protected LearningTask() {
    }

    public LearningTask(UUID bookingId, UUID teacherId, UUID studentId,
                        String title, String description, Priority priority, Instant deadline) {
        this.bookingId = bookingId;
        this.teacherId = teacherId;
        this.studentId = studentId;
        this.title = title;
        this.description = description;
        this.priority = priority == null ? Priority.NORMAL : priority;
        this.deadline = deadline;
    }

    /** 学生提交：仅待办状态可提交（重复提交被服务层幂等拦截）。 */
    public void submit(String content) {
        if (status != Status.PENDING) {
            throw new IllegalStateException("该任务已提交或已完成，请勿重复提交");
        }
        this.submissionContent = content;
        this.submittedAt = Instant.now();
        this.status = Status.SUBMITTED;
    }

    /** 教师反馈：提交态 → 完成。 */
    public void feedback(String text) {
        if (status != Status.SUBMITTED) {
            throw new IllegalStateException("仅已提交的任务可以反馈");
        }
        this.feedback = text;
        this.feedbackAt = Instant.now();
        this.status = Status.COMPLETED;
    }

    public boolean overdue() {
        return deadline != null && status == Status.PENDING && deadline.isBefore(Instant.now());
    }

    public Status getStatus() { return status; }
    public Priority getPriority() { return priority; }
    public UUID getBookingId() { return bookingId; }
    public UUID getTeacherId() { return teacherId; }
    public UUID getStudentId() { return studentId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public Instant getDeadline() { return deadline; }
    public String getSubmissionContent() { return submissionContent; }
    public Instant getSubmittedAt() { return submittedAt; }
    public String getFeedback() { return feedback; }
    public Instant getFeedbackAt() { return feedbackAt; }
}
