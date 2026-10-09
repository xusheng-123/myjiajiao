package com.nurture.shared.twophase;

import com.nurture.shared.domain.AggregateRoot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * 高后果操作确认凭证 — Preview 阶段预计算级联影响后签发，Execute 阶段消费。
 * 短效、一次性；库中仅存哈希。
 */
@Entity
@Table(name = "confirmation_grants",
        indexes = {
                @Index(name = "ux_confirmation_hash", columnList = "tokenHash", unique = true),
                @Index(name = "ix_confirmation_action", columnList = "actionType, aggregateId")
        })
public class ConfirmationGrant extends AggregateRoot {

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "action_type", nullable = false, length = 64)
    private String actionType;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "caller_id", nullable = false)
    private UUID callerId;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt = Instant.now();

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "preview_snapshot", columnDefinition = "text")
    private String previewSnapshot;

    protected ConfirmationGrant() {
    }

    public ConfirmationGrant(String tokenHash, String actionType, UUID aggregateId, UUID callerId,
                             Instant expiresAt, String previewSnapshot) {
        this.tokenHash = tokenHash;
        this.actionType = actionType;
        this.aggregateId = aggregateId;
        this.callerId = callerId;
        this.expiresAt = expiresAt;
        this.previewSnapshot = previewSnapshot;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public String getActionType() {
        return actionType;
    }

    public UUID getAggregateId() {
        return aggregateId;
    }

    public UUID getCallerId() {
        return callerId;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getConsumedAt() {
        return consumedAt;
    }

    public String getPreviewSnapshot() {
        return previewSnapshot;
    }

    public boolean usable() {
        return consumedAt == null && Instant.now().isBefore(expiresAt);
    }

    public void consume() {
        this.consumedAt = Instant.now();
    }
}
