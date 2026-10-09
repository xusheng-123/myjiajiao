package com.nurture.identity.app;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.nurture.shared.web.RateLimitExceededException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 注册频率限制 — 防脚本批量注册垃圾账户（账户滥用前置闸）。
 * 按 IP + 自然小时（Asia/Shanghai）计数，仅成功注册消耗配额（409 重复手机号不计）。
 * 阈值取宽限值 50/小时/IP：真实用户远达不到，脚本注册被有效限流。
 */
@Component
public class RegistrationRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RegistrationRateLimiter.class);
    private static final long HOURLY_LIMIT_PER_IP = 50;
    private static final DateTimeFormatter HOUR_BUCKET = DateTimeFormatter.ofPattern("yyyyMMdd-HH");
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final Cache<String, AtomicLong> counters = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofHours(1))
            .maximumSize(100_000)
            .build();

    /** 注册成功后调用：超过小时配额抛 429。 */
    public void consume(String clientIp) {
        String key = (clientIp == null || clientIp.isBlank() ? "unknown" : clientIp)
                + "@" + HOUR_BUCKET.withZone(ZONE).format(Instant.now());
        AtomicLong counter = counters.get(key, k -> new AtomicLong());
        long current = counter.incrementAndGet();
        if (current > HOURLY_LIMIT_PER_IP) {
            long wait = Math.max(1, 3600 - (Instant.now().getEpochSecond() % 3600));
            log.warn("[RegistrationLimiter] IP {} 本小时注册达 {} 次，触发限流", clientIp, current);
            throw new RateLimitExceededException("该网络环境注册过于频繁，请 " + wait + " 秒后再试", wait);
        }
    }
}
