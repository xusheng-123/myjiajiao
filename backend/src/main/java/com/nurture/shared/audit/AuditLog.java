package com.nurture.shared.audit;

import com.nurture.shared.domain.AggregateRoot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * 不可篡改审计日志 — 全量记录调用者身份、服务账号、物理时间戳、URI、动作类型、载荷哈希与执行状态。
 * 应用层仅插入、严禁更新与删除；数据库层以触发器拒绝 UPDATE/DELETE 兜底。
 */
@Entity
@Table(name = "audit_logs",
        indexes = {
                @Index(name = "ix_audit_caller", columnList = "callerId, occurredAt"),
                @Index(name = "ix_audit_action", columnList = "actionType, occurredAt")
        })
public class AuditLog extends AggregateRoot {

    @Column(name = "caller_id")
    private UUID callerId;

    @Column(name = "caller_role", length = 16)
    private String callerRole;

    @Column(name = "service_account", length = 128)
    private String serviceAccount;

    @Column(name = "occurred_at", nullable = false)
    private java.time.Instant occurredAt;

    @Column(name = "http_method", length = 8)
    private String httpMethod;

    @Column(name = "request_uri", length = 512)
    private String requestUri;

    @Column(name = "action_type", nullable = false, length = 64)
    private String actionType;

    @Column(name = "payload_hash", length = 64)
    private String payloadHash;

    @Column(name = "execution_status", nullable = false, length = 16)
    private String executionStatus;

    @Column(name = "detail", columnDefinition = "text")
    private String detail;

    protected AuditLog() {
    }

    public AuditLog(UUID callerId, String callerRole, String serviceAccount, java.time.Instant occurredAt,
                    String httpMethod, String requestUri, String actionType, String payloadHash,
                    String executionStatus, String detail) {
        this.callerId = callerId;
        this.callerRole = callerRole;
        this.serviceAccount = serviceAccount;
        this.occurredAt = occurredAt;
        this.httpMethod = httpMethod;
        this.requestUri = requestUri;
        this.actionType = actionType;
        this.payloadHash = payloadHash;
        this.executionStatus = executionStatus;
        this.detail = detail;
    }

    public UUID getCallerId() {
        return callerId;
    }

    public String getCallerRole() {
        return callerRole;
    }

    public String getServiceAccount() {
        return serviceAccount;
    }

    public java.time.Instant getOccurredAt() {
        return occurredAt;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public String getRequestUri() {
        return requestUri;
    }

    public String getActionType() {
        return actionType;
    }

    public String getPayloadHash() {
        return payloadHash;
    }

    public String getExecutionStatus() {
        return executionStatus;
    }

    public String getDetail() {
        return detail;
    }
}
