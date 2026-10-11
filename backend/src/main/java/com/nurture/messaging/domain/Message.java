package com.nurture.messaging.domain;

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
 * 站内消息 — 结构化系统消息与人工会话消息统一承载。
 */
@Entity
@Table(name = "messages", indexes = @Index(name = "ix_message_conversation", columnList = "conversationId, sentAt"))
public class Message extends AggregateRoot {

    public enum SenderRole { STUDENT, PARENT, TEACHER, ADMIN, SYSTEM }

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Column(name = "sender_id")
    private UUID senderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "sender_role", nullable = false, length = 16)
    private SenderRole senderRole;

    @Column(name = "body", columnDefinition = "text", nullable = false)
    private String body;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt = Instant.now();

    @Column(name = "read_at")
    private Instant readAt;

    private UUID attachmentId;
    public UUID getAttachmentId(){return attachmentId;}
    public void attach(UUID id){if(attachmentId!=null)throw new IllegalStateException("消息附件已固定");attachmentId=id;}

    protected Message() {
    }

    public Message(UUID conversationId, UUID senderId, SenderRole senderRole, String body) {
        this.conversationId = conversationId;
        this.senderId = senderId;
        this.senderRole = senderRole;
        this.body = body;
    }

    public void markRead() {
        if (readAt == null) {
            this.readAt = Instant.now();
        }
    }

    public UUID getConversationId() {
        return conversationId;
    }

    public UUID getSenderId() {
        return senderId;
    }

    public SenderRole getSenderRole() {
        return senderRole;
    }

    public String getBody() {
        return body;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public Instant getReadAt() {
        return readAt;
    }
}
