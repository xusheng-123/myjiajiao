package com.nurture.messaging.domain;

import com.nurture.shared.domain.AggregateRoot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * 课前咨询会话 — 学生与导师间的双向沟通线程。
 */
@Entity
@Table(name = "conversations", indexes = {
        @Index(name = "ix_conversation_student", columnList = "studentId, lastMessageAt"),
        @Index(name = "ix_conversation_teacher", columnList = "teacherId, lastMessageAt")
})
public class Conversation extends AggregateRoot {

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "teacher_id", nullable = false)
    private UUID teacherId;

    @Column(name = "subject", length = 64)
    private String subject;

    @Column(name = "last_message_at", nullable = false)
    private Instant lastMessageAt = Instant.now();

    protected Conversation() {
    }

    public Conversation(UUID studentId, UUID teacherId, String subject) {
        this.studentId = studentId;
        this.teacherId = teacherId;
        this.subject = subject;
    }

    public void touch() {
        this.lastMessageAt = Instant.now();
    }

    public UUID getStudentId() {
        return studentId;
    }

    public UUID getTeacherId() {
        return teacherId;
    }

    public String getSubject() {
        return subject;
    }

    public Instant getLastMessageAt() {
        return lastMessageAt;
    }
}
