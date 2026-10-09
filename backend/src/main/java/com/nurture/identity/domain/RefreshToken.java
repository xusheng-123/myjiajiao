package com.nurture.identity.domain;

import com.nurture.shared.domain.AggregateRoot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * 刷新令牌 — 库中仅存哈希，支持吊销。
 */
@Entity
@Table(name = "refresh_tokens",
        indexes = @Index(name = "ux_refresh_tokens_hash", columnList = "tokenHash", unique = true))
public class RefreshToken extends AggregateRoot {

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected RefreshToken() {
    }

    public RefreshToken(UUID accountId, String tokenHash, Instant expiresAt) {
        this.accountId = accountId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public boolean usable(Instant now) {
        return revokedAt == null && now.isBefore(expiresAt);
    }

    public UUID getAccountId() {
        return accountId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void revoke() {
        this.revokedAt = Instant.now();
    }
}
