package com.nurture.shared.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 安全配置属性。
 */
@ConfigurationProperties(prefix = "nurture.security")
public record SecurityProperties(
        Jwt jwt,
        ApiToken apiToken,
        BruteForce bruteForce
) {

    public record Jwt(
            String secret,
            String issuer,
            long accessTokenTtlMinutes,
            long refreshTokenTtlDays
    ) {
        public Duration accessTtl() {
            return Duration.ofMinutes(accessTokenTtlMinutes);
        }

        public Duration refreshTtl() {
            return Duration.ofDays(refreshTokenTtlDays);
        }
    }

    public record ApiToken(String prefix) {
    }

    /** 登录防暴力破解：账号维度阈值 + IP 维度阈值（防跨账号撞库）。 */
    public record BruteForce(
            long maxAttempts,
            long windowMinutes,
            long lockoutMinutes,
            long ipMaxAttempts
    ) {
        public long attempts() {
            return maxAttempts <= 0 ? 5 : maxAttempts;
        }

        public Duration window() {
            return Duration.ofMinutes(windowMinutes <= 0 ? 15 : windowMinutes);
        }

        public Duration lockout() {
            return Duration.ofMinutes(lockoutMinutes <= 0 ? 15 : lockoutMinutes);
        }

        public long ipAttempts() {
            return ipMaxAttempts <= 0 ? 100 : ipMaxAttempts;
        }
    }
}
