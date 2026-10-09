package com.nurture.learning.domain;

import com.nurture.shared.domain.AggregateRoot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** 课后答疑 — 学生向自己的教师提出结构化问题；教师回答后可关闭。 */
@Entity
@Table(name = "questions", indexes = {
        @Index(name = "ix_question_student", columnList = "studentId"),
        @Index(name = "ix_question_teacher", columnList = "teacherId, status")
})
public class Question extends AggregateRoot {

    public enum Status { OPEN, ANSWERED, CLOSED }

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "teacher_id", nullable = false)
    private UUID teacherId;

    @Column(name = "subject", nullable = false, length = 60)
    private String subject;

    @Column(name = "title", nullable = false, length = 120)
    private String title;

    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    private Status status = Status.OPEN;

    @Column(name = "answer", columnDefinition = "text")
    private String answer;

    @Column(name = "answered_at")
    private Instant answeredAt;

    protected Question() {
    }

    public Question(UUID studentId, UUID teacherId, String subject, String title, String content) {
        this.studentId = studentId;
        this.teacherId = teacherId;
        this.subject = subject;
        this.title = title;
        this.content = content;
    }

    public void answer(String text) {
        if (status == Status.CLOSED) {
            throw new IllegalStateException("问题已关闭，无法再回答");
        }
        this.answer = text;
        this.answeredAt = Instant.now();
        this.status = Status.ANSWERED;
    }

    public void close() {
        this.status = Status.CLOSED;
    }

    public Status getStatus() { return status; }
    public UUID getStudentId() { return studentId; }
    public UUID getTeacherId() { return teacherId; }
    public String getSubject() { return subject; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public String getAnswer() { return answer; }
    public Instant getAnsweredAt() { return answeredAt; }
}
