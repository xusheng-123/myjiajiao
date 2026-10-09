package com.nurture.messaging.domain;

import com.nurture.shared.domain.AggregateRoot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * 履约动态通知 — 结构化系统消息派发（预约确认、支付成功、审核结果等）。
 */
@Entity
@Table(name = "system_notices", indexes = @Index(name = "ix_notice_recipient", columnList = "recipientId, createdAt"))
public class SystemNotice extends AggregateRoot {

    @Column(name = "recipient_id", nullable = false)
    private UUID recipientId;

    @Column(name = "notice_type", nullable = false, length = 32)
    private String noticeType;

    @Column(name = "title", nullable = false, length = 128)
    private String title;

    @Column(name = "body", columnDefinition = "text")
    private String body;

    @Column(name = "read_at")
    private Instant readAt;

    protected SystemNotice() {
    }

    public SystemNotice(UUID recipientId, String noticeType, String title, String body) {
        this.recipientId = recipientId;
        this.noticeType = noticeType;
        this.title = title;
        this.body = body;
    }

    public void markRead() {
        if (readAt == null) {
            this.readAt = Instant.now();
        }
    }

    public UUID getRecipientId() {
        return recipientId;
    }

    public String getNoticeType() {
        return noticeType;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public Instant getReadAt() {
        return readAt;
    }
}
