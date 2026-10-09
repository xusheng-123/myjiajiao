package com.nurture.identity.infra;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 短信通道配置属性 — 发送限频与验证码试错上限。
 * 生产接入真实短信服务商后，限频参数应与服务商配额对齐。
 */
@ConfigurationProperties(prefix = "nurture.sms")
public record SmsProperties(RateLimit rateLimit, int verifyMaxAttempts) {

    public record RateLimit(long minIntervalSeconds, long hourlyLimit, long dailyLimit) {
    }

    public long minIntervalSeconds() {
        return rateLimit == null ? 60 : rateLimit.minIntervalSeconds();
    }

    public long hourlyLimit() {
        return rateLimit == null ? 5 : rateLimit.hourlyLimit();
    }

    public long dailyLimit() {
        return rateLimit == null ? 10 : rateLimit.dailyLimit();
    }

    public int verifyAttempts() {
        return verifyMaxAttempts <= 0 ? 5 : verifyMaxAttempts;
    }
}
