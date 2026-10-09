package com.nurture.identity.app;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.nurture.shared.audit.AuditService;
import com.nurture.shared.security.SecurityProperties;
import com.nurture.shared.web.RateLimitExceededException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 登录防暴力破解 — Caffeine 本地承载（规范禁止 Redis），双维度防护：
 * <ul>
 *   <li>账号维度：同一账号标识连续失败达到阈值即临时锁定（防单点穷举）；</li>
 *   <li>IP 维度：同一来源 IP 累计失败达到更高阈值即锁定该 IP（防跨账号撞库）。</li>
 * </ul>
 * 密码校验成功即清零该账号计数；锁定到期自动解除；运营可经 admin 端点手工解锁。
 * 锁定触发属于高后果安全事件，落审计流水。
 */
@Component
public class LoginThrottleService {

    private static final Logger log = LoggerFactory.getLogger(LoginThrottleService.class);

    private static final String ACCOUNT_PREFIX = "A:";
    private static final String IP_PREFIX = "IP:";

    private final SecurityProperties.BruteForce config;
    private final AuditService auditService;
    private final Cache<String, AtomicInteger> failureCounters;
    private final Cache<String, Long> lockedUntil;

    public LoginThrottleService(SecurityProperties securityProperties, AuditService auditService) {
        this.config = securityProperties.bruteForce();
        this.auditService = auditService;
        this.failureCounters = Caffeine.newBuilder()
                .expireAfterWrite(config.window())
                .maximumSize(100_000)
                .build();
        this.lockedUntil = Caffeine.newBuilder()
                .expireAfterWrite(config.lockout().plusMinutes(1))
                .maximumSize(100_000)
                .build();
    }

    /** 登录前调用：账号或 IP 处于锁定期即抛 429，并携带建议重试秒数。 */
    public void checkAllowed(String accountIdentifier, String clientIp) {
        Long accountLock = lockedUntil.getIfPresent(ACCOUNT_PREFIX + accountIdentifier);
        if (accountLock != null) {
            throw new RateLimitExceededException(
                    "该账号因多次登录失败已被临时锁定，请 " + remainingSeconds(accountLock) + " 秒后重试，"
                            + "或联系运营人员解锁",
                    remainingSeconds(accountLock));
        }
        Long ipLock = clientIp == null ? null : lockedUntil.getIfPresent(IP_PREFIX + clientIp);
        if (ipLock != null) {
            throw new RateLimitExceededException(
                    "当前网络环境登录失败次数过多已被临时限制，请 " + remainingSeconds(ipLock) + " 秒后重试",
                    remainingSeconds(ipLock));
        }
    }

    /** 密码校验失败后调用：达到阈值即触发锁定并落审计。 */
    public void recordFailure(String accountIdentifier, String clientIp) {
        long accountFailures = bump(ACCOUNT_PREFIX + accountIdentifier);
        if (accountFailures == config.attempts()) {
            lock(ACCOUNT_PREFIX + accountIdentifier, accountIdentifier,
                    "连续登录失败 " + accountFailures + " 次，账号临时锁定 " + config.lockout().toMinutes() + " 分钟");
        }
        if (clientIp != null) {
            long ipFailures = bump(IP_PREFIX + clientIp);
            if (ipFailures == config.ipAttempts()) {
                lock(IP_PREFIX + clientIp, clientIp,
                        "来源 IP 累计登录失败 " + ipFailures + " 次，IP 临时限制 " + config.lockout().toMinutes() + " 分钟");
            }
        }
    }

    /** 密码校验成功后调用：清零该账号失败计数并解除残留锁定。 */
    public void recordSuccess(String accountIdentifier) {
        failureCounters.invalidate(ACCOUNT_PREFIX + accountIdentifier);
        lockedUntil.invalidate(ACCOUNT_PREFIX + accountIdentifier);
    }

    /** 运营手工解锁（误锁申诉）：清空账号维度的计数与锁定；IP 维度限制不受影响。 */
    public void resetAccount(String accountIdentifier) {
        failureCounters.invalidate(ACCOUNT_PREFIX + accountIdentifier);
        lockedUntil.invalidate(ACCOUNT_PREFIX + accountIdentifier);
        log.info("[LoginThrottle] 运营解锁账号标识 {}，失败计数与锁定已清空", accountIdentifier);
    }

    /** 运营手工解除 IP 限制（与解锁账号配套提供）。 */
    public void resetIp(String clientIp) {
        failureCounters.invalidate(IP_PREFIX + clientIp);
        lockedUntil.invalidate(IP_PREFIX + clientIp);
        log.info("[LoginThrottle] 运营解除 IP 限制 {}", clientIp);
    }

    private long bump(String counterKey) {
        AtomicInteger counter = failureCounters.get(counterKey, k -> new AtomicInteger());
        return counter.incrementAndGet();
    }

    private void lock(String lockKey, String subject, String detail) {
        lockedUntil.put(lockKey, Instant.now().plus(config.lockout()).toEpochMilli());
        auditService.record("identity.login-lockout", "EXECUTED", subject, detail);
        log.warn("[LoginThrottle] {}（主体: {}）", detail, subject);
    }

    private long remainingSeconds(long untilEpochMs) {
        return Math.max(1, Duration.between(Instant.now(), Instant.ofEpochMilli(untilEpochMs)).getSeconds());
    }
}
