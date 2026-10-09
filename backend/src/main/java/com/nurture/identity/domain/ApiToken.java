package com.nurture.identity.domain;

import com.nurture.shared.domain.AggregateRoot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.UUID;

/**
 * Agent API Token — 独立 Service Account 凭证。
 * 明文仅在生成时刻展示一次，库中仅存不可逆加盐哈希（SHA-256(raw + salt)）；
 * lookup_hash 为未加盐定位哈希（唯一索引），验证时直查目标行避免全表遍历。
 */
@Entity
@Table(name = "api_tokens",
        indexes = {
                @Index(name = "ux_api_tokens_hash", columnList = "tokenHash", unique = true),
                @Index(name = "ux_api_tokens_lookup_hash", columnList = "lookupHash", unique = true)
        })
public class ApiToken extends AggregateRoot {

    @Column(name = "owner_account_id", nullable = false)
    private UUID ownerAccountId;

    @Column(name = "name", nullable = false, length = 64)
    private String name;

    @Column(name = "lookup_hash", nullable = false, length = 64)
    private String lookupHash;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "salt", nullable = false, length = 32)
    private String salt;

    /** 逗号分隔的 Scope 代码，最小权限显式声明。 */
    @Column(name = "scopes", nullable = false, length = 512)
    private String scopes;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    protected ApiToken() {
    }

    public ApiToken(UUID ownerAccountId, String name, String lookupHash, String tokenHash, String salt,
                    String scopes, Instant expiresAt) {
        this.ownerAccountId = ownerAccountId;
        this.name = name;
        this.lookupHash = lookupHash;
        this.tokenHash = tokenHash;
        this.salt = salt;
        this.scopes = scopes;
        this.expiresAt = expiresAt;
    }

    public boolean usable(Instant now) {
        return revokedAt == null && now.isBefore(expiresAt);
    }

    /** 盐值哈希二次确认：时间安全比较，防时序侧信道。 */
    public boolean hashMatches(String candidateHash) {
        return MessageDigest.isEqual(
                tokenHash.getBytes(StandardCharsets.UTF_8),
                candidateHash.getBytes(StandardCharsets.UTF_8));
    }

    public UUID getOwnerAccountId() {
        return ownerAccountId;
    }

    public String getName() {
        return name;
    }

    public String getLookupHash() {
        return lookupHash;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public String getSalt() {
        return salt;
    }

    public String getScopes() {
        return scopes;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }

    public void revoke() {
        this.revokedAt = Instant.now();
    }

    public void touchUsed() {
        this.lastUsedAt = Instant.now();
    }
}
