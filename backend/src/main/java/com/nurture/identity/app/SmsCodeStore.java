package com.nurture.identity.app;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.nurture.identity.infra.SmsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 短信验证码发送与校验 — Caffeine 本地缓存承载（规范禁止 Redis）。
 * 发送侧接入 {@link SmsRateLimiter} 三级限频；校验侧限制试错次数，
 * 防止 6 位数字码在 5 分钟有效期内被暴力穷举。
 * 生产接入真实短信服务商时替换投递通道。
 */
@Component
public class SmsCodeStore {

    private static final Logger log = LoggerFactory.getLogger(SmsCodeStore.class);
    private static final Random RANDOM = new SecureRandom();

    private final com.github.benmanes.caffeine.cache.Cache<String, String> codes =
            com.github.benmanes.caffeine.cache.Caffeine.newBuilder()
                    .expireAfterWrite(Duration.ofMinutes(5))
                    .maximumSize(10_000)
                    .build();

    /** 校验失败计数：与验证码同生命周期（5 分钟），达到上限即作废验证码。 */
    private final Cache<String, AtomicInteger> failedVerifications =
            Caffeine.newBuilder()
                    .expireAfterWrite(Duration.ofMinutes(5))
                    .maximumSize(10_000)
                    .build();

    private final SmsRateLimiter rateLimiter;
    private final SmsProperties properties;
    private final SmsDelivery delivery;

    public SmsCodeStore(SmsRateLimiter rateLimiter, SmsProperties properties, SmsDelivery delivery) {
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.delivery = delivery;
    }

    public boolean available() { return delivery.available(); }

    public void send(String role, String phone) {
        if (!delivery.available()) throw new com.nurture.shared.web.ServiceUnavailableException("短信服务尚未开通，请使用账号密码登录");
        rateLimiter.checkAndConsume(phone);
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        delivery.send(phone, code);
        codes.put(key(role, phone), code);
        failedVerifications.invalidate(key(role, phone));
        // 安全纪律（Release P1-3）：验证码严禁出现在任何日志中；手机号脱敏
        log.info("[SmsSender] 短信投递通道已接受 {}（{}角色）的验证码请求；验证码内容不落日志，试错上限 {} 次",
                maskPhone(phone), role, properties.verifyAttempts());
    }

    public boolean verify(String role, String phone, String code) {
        String cacheKey = key(role, phone);
        String expected = codes.getIfPresent(cacheKey);
        if (expected == null) {
            return false;
        }
        if (expected.equals(code)) {
            codes.invalidate(cacheKey);
            failedVerifications.invalidate(cacheKey);
            return true;
        }
        AtomicInteger failures = failedVerifications.get(cacheKey, k -> new AtomicInteger());
        if (failures.incrementAndGet() >= properties.verifyAttempts()) {
            // 试错次数耗尽：作废验证码，须重新发送（每次重发都会重置计数）
            codes.invalidate(cacheKey);
            log.warn("[SmsCodeStore] 手机号 {} 验证码试错达 {} 次，已作废当前验证码", maskPhone(phone), properties.verifyAttempts());
        }
        return false;
    }

    /** 手机号脱敏：保留前 3 后 4，中间以 **** 掩盖（日志与审计输出纪律）。 */
    static String maskPhone(String phone) {
        if (phone == null || phone.length() < 8) {
            return "***";
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    private String key(String role, String phone) {
        return role + ":" + phone;
    }
}
