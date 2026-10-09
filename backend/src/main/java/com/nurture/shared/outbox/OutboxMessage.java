package com.nurture.shared.outbox;

import com.nurture.shared.domain.AggregateRoot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * 事务发件箱记录 — 规范禁止 Kafka/RocketMQ，进程内解耦经由 Spring ApplicationEvent，
 * 需要可靠投递时写入本表并轮询派发，与业务同事务提交。
 */
@Entity
@Table(name = "outbox_messages",
        indexes = @Index(name = "ix_outbox_pending", columnList = "dispatchedAt, occurredAt"))
public class OutboxMessage extends AggregateRoot {

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "aggregate_type", nullable = false, length = 32)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "payload", columnDefinition = "text", nullable = false)
    private String payload;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt = Instant.now();

    @Column(name = "dispatched_at")
    private Instant dispatchedAt;

    @Column(name = "attempts", nullable = false)
    private int attempts = 0;

    protected OutboxMessage() {
    }

    public OutboxMessage(String eventType, String aggregateType, UUID aggregateId, String payload) {
        this.eventType = eventType;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.payload = payload;
    }

    public String getEventType() {
        return eventType;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public UUID getAggregateId() {
        return aggregateId;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Instant getDispatchedAt() {
        return dispatchedAt;
    }

    public int getAttempts() {
        return attempts;
    }

    public void markDispatched() {
        this.dispatchedAt = Instant.now();
    }

    public void markAttemptFailed() {
        this.attempts++;
    }

    public boolean pending() {
        return dispatchedAt == null;
    }
}
