package com.nurture.it;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 注册限流集成专项（真 PostgreSQL、全过滤器链）：
 * 限流键 = X-Forwarded-For 首跳（AuthController.clientIp，生产经 Cloudflare Tunnel 透传）。
 * 验证生产规则在真实过滤器链下生效：同一来源打满 50 次/自然小时后第 51 次 429（业务文案）、
 * 换来源不受影响（桶独立）、无 XFF 时回落 remoteAddr 不影响注册主链路。
 * 阈值与桶逻辑的单元覆盖见 RegistrationRateLimiterTest；
 * 其余 IT 经 Support.register 以「每测试类独立虚拟来源」隔离，不影响本类显式固定来源的验证。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("it")
@Tag("integration")
class RegistrationRateLimitIT {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    private final Random random = new Random();

    private String phone() {
        return "17" + String.format("%09d", random.nextInt(1_000_000_000));
    }

    private record RegisterResult(int status, String body) {
    }

    private RegisterResult register(String forwardedFor, String phone) throws Exception {
        var body = new HashMap<String, Object>();
        body.put("displayName", "IT-LimitProbe");
        body.put("phone", phone);
        body.put("password", "ItLimit#2026");
        var builder = post("/api/v1/identity/auth/register:student")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsBytes(body))
                .header("Idempotency-Key", UUID.randomUUID().toString());
        if (forwardedFor != null) {
            builder.header("X-Forwarded-For", forwardedFor);
        }
        var result = mvc.perform(builder).andReturn();
        return new RegisterResult(result.getResponse().getStatus(),
                result.getResponse().getContentAsString());
    }

    @Test
    void 同一来源打满小时配额后429_换来源不受影响_无头回落可用() throws Exception {
        String saturated = "10.203.77.1";
        for (int i = 0; i < 50; i++) {
            var r = register(saturated, phone());
            assertThat(r.status()).as("同来源第 %d 次注册应成功", i + 1).isEqualTo(200);
        }
        var rejected = register(saturated, phone());
        assertThat(rejected.status()).as("同来源第 51 次注册应被限流").isEqualTo(429);
        assertThat(rejected.body()).contains("注册过于频繁");

        var otherSource = register("10.203.78.1", phone());
        assertThat(otherSource.status()).as("换来源（XFF 首跳变化）配额独立").isEqualTo(200);

        var fallback = register(null, phone());
        assertThat(fallback.status()).as("无 XFF 回落 remoteAddr，注册主链路不受影响").isEqualTo(200);
    }
}
