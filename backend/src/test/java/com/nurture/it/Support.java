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
        return register(mvc, om, rolePath, role, phone, password, callerClassIp());
    }

    /**
     * 注册限流键 = X-Forwarded-For 首跳（AuthController.clientIp：生产经 Cloudflare Tunnel
     * 透传真实客户端 IP，无头回落 remoteAddr）。MockMvc 不经网络，全部请求 remoteAddr 相同，
     * 不带 XFF 时整套件 ≈52 次注册共享同一「50 次/自然小时/IP」桶，尾部 IT 必然 429。
     * 以调用测试类为单位分配稳定虚拟来源：类间桶独立、单类注册量（≤6）远低于生产阈值，
     * 生产限流规则零改动；XFF→限流键的接线行为由 RegistrationRateLimitIT 专项验证。
     */
    public static Account register(MockMvc mvc, ObjectMapper om, String rolePath, String role,
                                   String phone, String password, String forwardedFor) throws Exception {
        var body = new java.util.HashMap<String, Object>();
        body.put("displayName", "IT-" + role);
        body.put("phone", phone);
        body.put("password", password);
        if (role.equals("TEACHER")) {
            body.put("school", "集成测试大学");
            body.put("highestDegree", "硕士");
            body.put("yearsOfTeaching", 2);
        }
        var builder = post("/api/v1/identity/auth/register:" + role_path(role))
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsBytes(body))
                .header("Idempotency-Key", UUID.randomUUID().toString());
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            builder.header("X-Forwarded-For", forwardedFor);
        }
        var resp = mvc.perform(builder)
                .andExpect(r -> { if (r.getResponse().getStatus() != 200) {
                    throw new AssertionError("注册失败: " + r.getResponse().getStatus() + " " + r.getResponse().getContentAsString());
                } })
                .andReturn();
        JsonNode node = om.readTree(resp.getResponse().getContentAsString());
        // accountNo 是业务编号（非 UUID）；账户 ID 从 JWT subject 解码
        return new Account(phone, node.get("accessToken").asText(),
                UUID.fromString(jwtSub(node.get("accessToken").asText())));
    }

    /** 调用测试类的稳定虚拟来源 IP（10.h.h.1 私有段）：同一类内共享来源、类间互不影响。 */
    static String callerClassIp() {
        String caller = StackWalker.getInstance().walk(s -> s
                .map(StackWalker.StackFrame::getClassName)
                .filter(name -> !name.equals(Support.class.getName()))
                .findFirst()
                .orElse("unknown"));
        int h = Math.abs(caller.hashCode());
        return "10." + (h % 200 + 2) + "." + (h / 200 % 200 + 2) + ".1";
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
