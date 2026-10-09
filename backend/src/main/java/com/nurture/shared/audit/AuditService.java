package com.nurture.shared.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nurture.shared.security.CallerContext;
import com.nurture.shared.security.CallerPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

/**
 * 审计服务 — 高后果动作（退款、取消、审核、支付回调）必须落审计流水。
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository repository;
    private final ObjectMapper objectMapper;

    public AuditService(AuditLogRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public void record(String actionType, String executionStatus, Object payload, String detail) {
        CallerPrincipal caller = CallerContext.get();
        repository.save(new AuditLog(
                caller == null ? null : caller.accountId(),
                caller == null ? "SYSTEM" : caller.role().name(),
                caller != null && caller.role() == com.nurture.shared.security.CallerRole.AGENT
                        ? "agent:" + caller.accountId() : null,
                Instant.now(),
                null, null,
                actionType, hash(payload), executionStatus, detail));
    }

    public void recordHttp(String actionType, String httpMethod, String uri, String executionStatus, Object payload) {
        CallerPrincipal caller = CallerContext.get();
        repository.save(new AuditLog(
                caller == null ? null : caller.accountId(),
                caller == null ? "SYSTEM" : caller.role().name(),
                null,
                Instant.now(),
                httpMethod, uri,
                actionType, hash(payload), executionStatus, null));
    }

    private String hash(Object payload) {
        if (payload == null) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = payload instanceof String s
                    ? s.getBytes(java.nio.charset.StandardCharsets.UTF_8)
                    : objectMapper.writeValueAsBytes(payload);
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (Exception e) {
            log.warn("审计载荷哈希失败", e);
            return null;
        }
    }
}
