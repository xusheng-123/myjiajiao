package com.nurture.shared.idempotency;

import com.nurture.shared.domain.AggregateRoot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * 幂等防重记录 — 基于 PostgreSQL 唯一索引建立，防止智能体重试造成重复扣费或重复预约。
 * 键 = 调用者 + HTTP 方法 + URI + Idempotency-Key；响应快照用于幂等重放。
 */
@Entity
@Table(name = "idempotency_keys",
        indexes = @Index(name = "ux_idempotency_fingerprint", columnList = "callerId, httpMethod, requestUri, idempotencyKey", unique = true))
public class IdempotencyRecord extends AggregateRoot {

    @Column(name = "caller_id", nullable = false)
    private UUID callerId;

    @Column(name = "http_method", nullable = false, length = 8)
    private String httpMethod;

    @Column(name = "request_uri", nullable = false, length = 512)
    private String requestUri;

    @Column(name = "idempotency_key", nullable = false, length = 64)
    private String idempotencyKey;

    @Column(name = "request_fingerprint", nullable = false)
    private String requestFingerprint;

    @Column(name = "response_status", nullable = false)
    private int responseStatus;

    @Column(name = "response_snapshot", columnDefinition = "text")
    private String responseSnapshot;

    protected IdempotencyRecord() {
    }

    public IdempotencyRecord(UUID callerId, String httpMethod, String requestUri,
                             String idempotencyKey, String requestFingerprint) {
        this.callerId = callerId;
        this.httpMethod = httpMethod;
        this.requestUri = requestUri;
        this.idempotencyKey = idempotencyKey;
        this.requestFingerprint = requestFingerprint;
    }

    public UUID getCallerId() {
        return callerId;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public String getRequestUri() {
        return requestUri;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestFingerprint() {
        return requestFingerprint;
    }

    public int getResponseStatus() {
        return responseStatus;
    }

    public String getResponseSnapshot() {
        return responseSnapshot;
    }

    public void recordResponse(int status, String snapshot) {
        this.responseStatus = status;
        this.responseSnapshot = snapshot;
    }

    /** 首次到达时响应快照尚未生成。 */
    public boolean completed() {
        return responseSnapshot != null;
    }
}
