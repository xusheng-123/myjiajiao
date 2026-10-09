package com.nurture.identity.app;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.nurture.identity.infra.SmsProperties;
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
 * 短信发送限频器 — Caffeine 本地承载（规范禁止 Redis），三级窗口防短信轰炸：
 * ① 同号最小间隔；② 自然小时配额；③ 自然日配额（时区 Asia/Shanghai）。
 * 仅成功发送消耗配额，被拒请求不落计数。
 */
@Component
public class SmsRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(SmsRateLimiter.class);
    private static final DateTimeFormatter HOUR_BUCKET = DateTimeFormatter.ofPattern("yyyyMMdd-HH");
    private static final DateTimeFormatter DAY_BUCKET = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final SmsProperties properties;
    private final Cache<String, Long> lastSentAt;
    private final Cache<String, AtomicLong> hourlyCounters;
    private final Cache<String, AtomicLong> dailyCounters;

    public SmsRateLimiter(SmsProperties properties) {
        this.properties = properties;
        this.lastSentAt = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(Math.max(1, properties.minIntervalSeconds())))
                .maximumSize(100_000)
                .build();
        this.hourlyCounters = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofHours(1))
                .maximumSize(100_000)
                .build();
        this.dailyCounters = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofHours(25))
                .maximumSize(100_000)
                .build();
    }

    /** 发送前调用：超出任一窗口即抛 429，并携带建议重试秒数；全部通过则记一次成功发送。 */
    public void checkAndConsume(String phone) {
        Instant now = Instant.now();
        Long last = lastSentAt.getIfPresent(phone);
        if (last != null) {
            long wait = Math.max(1, properties.minIntervalSeconds()
                    - Duration.ofMillis(now.toEpochMilli() - last).getSeconds());
            throw new RateLimitExceededException("验证码发送过于频繁，请 " + wait + " 秒后再试", wait);
        }
        String hourKey = bucketKey(phone, HOUR_BUCKET, now);
        if (!tryConsume(hourlyCounters, hourKey, properties.hourlyLimit())) {
            throw new RateLimitExceededException("该手机号本小时验证码发送次数已达上限，请稍后再试",
                    secondsToNextHour(now));
        }
        String dayKey = bucketKey(phone, DAY_BUCKET, now);
        if (!tryConsume(dailyCounters, dayKey, properties.dailyLimit())) {
            // 小时配额已消耗 1 次：属滥用请求，不回收
            throw new RateLimitExceededException("该手机号今日验证码发送次数已达上限，请明日再试或联系运营",
                    secondsToNextDay(now));
        }
        lastSentAt.put(phone, now.toEpochMilli());
        log.info("[SmsRateLimiter] 手机号 {} 通过限频校验（本小时第 {} 条 / 今日第 {} 条）", phone,
                counterOf(hourlyCounters, hourKey).get(), counterOf(dailyCounters, dayKey).get());
    }

    private String bucketKey(String phone, DateTimeFormatter format, Instant now) {
        return phone + "@" + format.withZone(ZONE).format(now);
    }

    /** CAS 原子“检查并占用一个名额”，避免并发下越过上限。 */
    private boolean tryConsume(Cache<String, AtomicLong> cache, String key, long limit) {
        AtomicLong counter = counterOf(cache, key);
        for (; ; ) {
            long current = counter.get();
            if (current >= limit) {
                return false;
            }
            if (counter.compareAndSet(current, current + 1)) {
                return true;
            }
        }
    }

    private AtomicLong counterOf(Cache<String, AtomicLong> cache, String key) {
        return cache.get(key, k -> new AtomicLong());
    }

    private long secondsToNextHour(Instant now) {
        return Math.max(1, 3600 - (now.getEpochSecond() % 3600));
    }

    private long secondsToNextDay(Instant now) {
        long secondsToday = now.atZone(ZONE).toLocalDate().atStartOfDay(ZONE)
                .plusDays(1).toEpochSecond() - now.getEpochSecond();
        return Math.max(1, secondsToday);
    }
}
