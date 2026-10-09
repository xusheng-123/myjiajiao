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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * D3 集成回归（真 PostgreSQL + 真过滤器链）：运营锁定账户后，
 * 被锁账户的存量 Access Token 在下一次请求立即被拒（零缓存窗口）；解锁后重新登录可用。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("it")
@Tag("integration")
class AccountLockoutIT {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    private final Random random = new Random();

    private String phone() {
        return "18" + String.format("%09d", random.nextInt(1_000_000_000));
    }

    private String adminToken() throws Exception {
        var resp = mvc.perform(post("/api/v1/identity/auth/login:password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsBytes(new HashMap<String, String>() {{
                            put("role", "ADMIN");
                            put("account", "13800000000");
                            put("password", "Admin#2026");
                        }}))
                        .header("Idempotency-Key", java.util.UUID.randomUUID().toString()))
                .andExpect(status().isOk()).andReturn();
        return om.readTree(resp.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private String accountIdOf(String token) throws Exception {
        var profile = mvc.perform(get("/api/v1/student/me/profile")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        return om.readTree(profile.getResponse().getContentAsString()).get("accountId").asText();
    }

    @Test
    void 锁定后存量令牌立即失效_解锁后重新登录可用() throws Exception {
        var student = Support.register(mvc, om, "student", "STUDENT", phone(), "ItStudent#1");
        String token = student.accessToken();
        String accountId = accountIdOf(token);

        // 锁定前：正常访问
        mvc.perform(get("/api/v1/student/me/profile").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // 运营锁定
        mvc.perform(post("/api/v1/admin/accounts/%s:lock".formatted(accountId))
                        .header("Authorization", "Bearer " + adminToken())
                        .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"集成测试锁定\"}"))
                .andExpect(status().is2xxSuccessful());

        // 锁定后：存量令牌在下一次请求立即被拒（D3 零窗口）
        mvc.perform(get("/api/v1/student/me/profile").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        // 解锁
        mvc.perform(post("/api/v1/admin/accounts/%s:unlock".formatted(accountId))
                        .header("Authorization", "Bearer " + adminToken())
                        .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"集成测试解锁\"}"))
                .andExpect(status().is2xxSuccessful());

        // 解锁后重新登录可用（旧令牌在锁定期间已被闸门拒绝，重新认证恢复正常）
        var relogin = mvc.perform(post("/api/v1/identity/auth/login:password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsBytes(new HashMap<String, String>() {{
                            put("role", "STUDENT");
                            put("account", student.phone());
                            put("password", "ItStudent#1");
                        }}))
                        .header("Idempotency-Key", java.util.UUID.randomUUID().toString()))
                .andExpect(status().isOk()).andReturn();
        String newToken = om.readTree(relogin.getResponse().getContentAsString()).get("accessToken").asText();
        mvc.perform(get("/api/v1/student/me/profile").header("Authorization", "Bearer " + newToken))
                .andExpect(status().isOk());
    }
}
