package com.nurture.shared.twophase;

import com.nurture.shared.security.CallerContext;
import com.nurture.shared.security.CallerPrincipal;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

/**
 * 两阶段执行（Preview/Execute）支撑服务。
 *
 * <p>针对退款、取消课时等不可逆动作强制拆分为两阶段接口：
 * 阶段一 Preview 调用受控端点预计算级联影响，返回短效 Confirmation Token；
 * 阶段二 Execute 由 Agent/用户携带 Confirmation Token 与幂等键发起终态确认，原子性完成变更。</p>
 */
@Service
public class TwoPhaseService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(10);

    private final ConfirmationGrantRepository repository;

    public TwoPhaseService(ConfirmationGrantRepository repository) {
        this.repository = repository;
    }

    /** Preview 阶段末尾调用：签发短效一次性确认凭证并返回明文（仅此一次可见）。 */
    public Grant issue(String actionType, UUID aggregateId, String previewSnapshot) {
        CallerPrincipal caller = CallerContext.require();
        String rawToken = UUID.randomUUID().toString().replace("-", "") + RANDOMToken();
        String hash = sha256(rawToken);
        Instant expiresAt = Instant.now().plus(DEFAULT_TTL);
        repository.save(new ConfirmationGrant(hash, actionType, aggregateId, caller.accountId(), expiresAt, previewSnapshot));
        return new Grant(rawToken, expiresAt);
    }

    /** Execute 阶段调用：校验凭证（匹配动作/聚合/调用者、未过期、未消费）并原子消费。 */
    public ConfirmationGrant consume(String actionType, UUID aggregateId, String rawToken) {
        CallerPrincipal caller = CallerContext.require();
        ConfirmationGrant grant = repository.findByTokenHash(sha256(rawToken))
                .orElseThrow(() -> new IllegalArgumentException("确认凭证无效：请先调用 Preview 阶段重新获取"));
        if (!grant.usable()) {
            throw new IllegalStateException("确认凭证已过期或已使用，请重新执行 Preview");
        }
        if (!grant.getActionType().equals(actionType) || !grant.getAggregateId().equals(aggregateId)
                || !grant.getCallerId().equals(caller.accountId())) {
            throw new IllegalArgumentException("确认凭证与当前操作不匹配");
        }
        grant.consume();
        return grant;
    }

    private String RANDOMToken() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public record Grant(String confirmationToken, Instant expiresAt) {
    }
}
