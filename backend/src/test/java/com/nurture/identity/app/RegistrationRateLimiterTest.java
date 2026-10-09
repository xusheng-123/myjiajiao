package com.nurture.identity.app;

import com.nurture.shared.web.RateLimitExceededException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** D8 配套（注册限频）：仅成功注册计数；同 IP 超阈值拒绝，跨 IP 互不影响。 */
class RegistrationRateLimiterTest {

    @Test
    void 同IP达到小时配额后拒绝() {
        var limiter = new RegistrationRateLimiter();
        for (int i = 0; i < 50; i++) {
            assertThatCode(() -> limiter.consume("10.0.0.1")).doesNotThrowAnyException();
        }
        assertThatThrownBy(() -> limiter.consume("10.0.0.1"))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessageContaining("注册过于频繁");
    }

    @Test
    void 不同IP配额独立() {
        var limiter = new RegistrationRateLimiter();
        for (int i = 0; i < 50; i++) {
            limiter.consume("10.0.0.2");
        }
        assertThatCode(() -> limiter.consume("10.0.0.3")).doesNotThrowAnyException();
    }

    @Test
    void 空IP归入未知桶() {
        var limiter = new RegistrationRateLimiter();
        assertThatCode(() -> limiter.consume(null)).doesNotThrowAnyException();
        assertThatCode(() -> limiter.consume("")).doesNotThrowAnyException();
    }
}
