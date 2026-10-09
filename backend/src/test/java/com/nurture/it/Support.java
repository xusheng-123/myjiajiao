package com.nurture.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;


import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 集成测试公共工具：注册/登录走真实 API（走全部过滤器链）。 */
public final class Support {

    public static JsonNode postJson(MockMvc mvc, ObjectMapper om, String url, String token, Object body) throws Exception {
        var builder = post(url).contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsBytes(body))
                .header("Idempotency-Key", UUID.randomUUID().toString());
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        var result = mvc.perform(builder).andReturn();
        String content = result.getResponse().getContentAsString();
        return content.isBlank() ? om.nullNode() : om.readTree(content);
    }

    public static JsonNode getJson(MockMvc mvc, ObjectMapper om, String url, String token) throws Exception {
        var builder = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(url);
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        var result = mvc.perform(builder).andReturn();
        String content = result.getResponse().getContentAsString();
        return content.isBlank() ? om.nullNode() : om.readTree(content);
    }

    public record Account(String phone, String accessToken, UUID accountId) {
    }

    public static Account register(MockMvc mvc, ObjectMapper om, String rolePath, String role,
                                   String phone, String password) throws Exception {
        var body = new java.util.HashMap<String, Object>();
        body.put("displayName", "IT-" + role);
        body.put("phone", phone);
        body.put("password", password);
        if (role.equals("TEACHER")) {
            body.put("school", "集成测试大学");
            body.put("highestDegree", "硕士");
            body.put("yearsOfTeaching", 2);
        }
        var resp = mvc.perform(post("/api/v1/identity/auth/register:" + role_path(role))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsBytes(body))
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(r -> { if (r.getResponse().getStatus() != 200) {
                    throw new AssertionError("注册失败: " + r.getResponse().getStatus() + " " + r.getResponse().getContentAsString());
                } })
                .andReturn();
        JsonNode node = om.readTree(resp.getResponse().getContentAsString());
        // accountNo 是业务编号（非 UUID）；账户 ID 从 JWT subject 解码
        return new Account(phone, node.get("accessToken").asText(),
                UUID.fromString(jwtSub(node.get("accessToken").asText())));
    }

    /** AuthResponse 不含 accountId——从 JWT subject 解码。 */
    private static String jwtSub(String token) {
        String payload = token.split("\\.")[1];
        payload += "=".repeat((4 - payload.length() % 4) % 4);
        try {
            return new String(java.util.Base64.getUrlDecoder().decode(payload))
                    .replaceAll(".*\"sub\":\"([^\"]+)\".*", "$1");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String role_path(String role) {
        return role.toLowerCase();
    }
}
