package com.nurture.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nurture.identity.app.IdentityApplicationService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 支付模式 B「运营线下收款确认」状态机与幂等实测（真 PostgreSQL，走完整过滤器链）。
 * 覆盖：权限矩阵 / 状态迁移矩阵 / 重复操作 / 幂等键重放 / 连点 / 金额不可篡改 / 结算通知审计单次性。
 * 注意：审计行 payload 为哈希存储（无明文 orderId），故审计断言采用全局计数增量法。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("it")
@Tag("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OfflinePaymentStateMachineIT {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired JdbcTemplate jdbc;
    @Autowired IdentityApplicationService identityService;

    private String tAdmin;
    private String tAdmin2;
    private String tTeacher;
    private String tStudent;
    private String tParent;
    private String teacherId;
    private UUID baseOrder;
    private UUID baseBooking;
    /** 正常确认完成后的 offline-confirm 审计总数（后续重复操作不得增长） */
    private int auditTotalAfterHappy = -1;

    /** 生成合法测试手机号：1[3-9] + 9 位（前缀 3 位 + suffix 7 位 + 序号 1 位 → 共 11 位）。 */
    private static String phone(String uuidSuffix, int seq) {
        String digits = uuidSuffix.replaceAll("[^0-9]", "x");
        return "137" + digits + seq; // 3 位前缀 + suffix 纯数字 7 位 + 序号 = 11 位
    }

    @BeforeAll
    void setUpAll() throws Exception {
        String suffix = String.format("%07d", new java.security.SecureRandom().nextInt(10_000_000));
        var teacher = Support.register(mvc, om, "teacher", "TEACHER", phone(suffix, 1), "PayIT#2026");
        var student = Support.register(mvc, om, "student", "STUDENT", phone(suffix, 2), "PayIT#2026");
        var parent = Support.register(mvc, om, "parent", "PARENT", phone(suffix, 3), "PayIT#2026");
        tTeacher = teacher.accessToken();
        tStudent = student.accessToken();
        tParent = parent.accessToken();
        teacherId = teacher.accountId().toString();
        // 两个运营账号：经 identity 服务直接创建（现有 API 无建号端点），登录走真实 API
        identityService.createAdminAccount("136" + suffix + "10", "运营A", "PayAdmin#2026");
        identityService.createAdminAccount("136" + suffix + "20", "运营B", "PayAdmin#2026");
        tAdmin = login("136" + suffix + "10", "PayAdmin#2026");
        tAdmin2 = login("136" + suffix + "20", "PayAdmin#2026");
        // 监护绑定链路：家长发起 + 学生确认
        String studentNo = Support.getJson(mvc, om, "/api/v1/student/me/profile", tStudent)
                .get("studentNo").asText();
        Support.postJson(mvc, om, "/api/v1/parent/me/children", tParent,
                new java.util.HashMap<>() {{ put("studentNo", studentNo); put("relation", "母亲"); }});
        Support.postJson(mvc, om, "/api/v1/parent/me/children:confirm", tStudent, new java.util.HashMap<>());
    }

    private String login(String account, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/identity/auth/login:password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsBytes(new java.util.HashMap<>() {{
                            put("role", "ADMIN"); put("account", account); put("password", password);
                        }}))
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isOk()).andReturn();
        return om.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    /** 独立链路：新档期 → 试听预约（自动确认）→ 家长下单 → PENDING_PAYMENT 订单。 */
    private JsonNode createPendingOrder(String subject) throws Exception {
        java.security.SecureRandom rnd = new java.security.SecureRandom();
        var start = OffsetDateTime.now().plusDays(20 + rnd.nextInt(60))
                .withHour(8 + rnd.nextInt(12)).withMinute(rnd.nextInt(12) * 5).withSecond(0).withNano(0);
        var slot = Support.postJson(mvc, om, "/api/v1/scheduling/availability-slots", tTeacher,
                new java.util.HashMap<>() {{ put("startAt", start.toString()); put("endAt", start.plusHours(1).toString()); }});
        var booking = Support.postJson(mvc, om, "/api/v1/booking/bookings", tStudent,
                new java.util.HashMap<>() {{
                    put("teacherId", teacherId); put("slotId", slot.get("slotId").asText());
                    put("subject", subject); put("bookingType", "TRIAL");
                }});
        return Support.postJson(mvc, om, "/api/v1/order/orders", tParent,
                new java.util.HashMap<>() {{ put("bookingId", booking.get("bookingId").asText()); }});
    }

    private MvcResult rawPost(String url, String token, Object body, String idem) throws Exception {
        var builder = post(url).contentType(MediaType.APPLICATION_JSON).header("Idempotency-Key", idem);
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        if (body != null) {
            builder.content(om.writeValueAsBytes(body));
        }
        return mvc.perform(builder).andReturn();
    }

    private int count(String sql, Object... args) {
        Integer n = jdbc.queryForObject(sql, Integer.class, args);
        return n == null ? 0 : n;
    }

    private int offlineConfirmAuditCount() {
        return count("SELECT count(*) FROM audit_logs WHERE action_type='payment.offline-confirm' "
                + "AND execution_status='SUCCESS'");
    }

    private String orderStatus(UUID orderId) {
        return jdbc.queryForObject("SELECT status FROM orders WHERE id=?", String.class, orderId);
    }

    // ================================================================ 权限矩阵

    @Test
    @Order(10)
    @DisplayName("权限：学生/家长/导师确认线下收款均被拒(403)，订单与支付记录不变")
    void nonAdminRejected() throws Exception {
        var order = createPendingOrder("权限矩阵");
        baseOrder = UUID.fromString(order.get("orderId").asText());
        baseBooking = UUID.fromString(order.get("bookingId").asText());
        for (String token : new String[]{tStudent, tParent, tTeacher}) {
            var result = rawPost(confirmUrl(baseOrder), token, null, UUID.randomUUID().toString());
            assertThat(result.getResponse().getStatus()).as("非运营必须 403").isEqualTo(403);
        }
        var anonymous = rawPost(confirmUrl(baseOrder), null, null, UUID.randomUUID().toString());
        int anonStatus = anonymous.getResponse().getStatus();
        assertThat(anonStatus).as("匿名必须 401/403").isIn(401, 403);
        assertThat(orderStatus(baseOrder)).isEqualTo("PENDING_PAYMENT");
        assertThat(count("SELECT count(*) FROM payment_attempts WHERE order_id=?", baseOrder)).isZero();
    }

    // ================================================================ 正常确认 + 单次性

    @Test
    @Order(20)
    @DisplayName("ADMIN 正常确认：PENDING_PAYMENT→PAID，支付/结算/通知/审计各恰好一次")
    void adminConfirmHappyPath() throws Exception {
        int auditBefore = offlineConfirmAuditCount();
        var result = rawPost(confirmUrl(baseOrder), tAdmin, null, UUID.randomUUID().toString());
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = om.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("status").asText()).isEqualTo("PAID");
        assertThat(body.get("channel").asText()).isEqualTo("OFFLINE");
        long serverAmount = jdbc.queryForObject(
                "SELECT amount_cents FROM orders WHERE id=?", Long.class, baseOrder);
        assertThat(body.get("amountCents").asLong()).isEqualTo(serverAmount);
        assertThat(orderStatus(baseOrder)).isEqualTo("PAID");
        assertSingleSideEffects(baseBooking);
        auditTotalAfterHappy = offlineConfirmAuditCount();
        assertThat(auditTotalAfterHappy).as("正常确认必须产生恰好 1 条审计").isEqualTo(auditBefore + 1);
    }

    @Test
    @Order(21)
    @DisplayName("重复确认（不同幂等键 ×3）：幂等返回，审计与副作用零增长")
    void repeatConfirmIdempotent() throws Exception {
        for (int i = 0; i < 3; i++) {
            var result = rawPost(confirmUrl(baseOrder), tAdmin, null, UUID.randomUUID().toString());
            assertThat(result.getResponse().getStatus()).isEqualTo(200);
            assertThat(om.readTree(result.getResponse().getContentAsString()).get("idempotent").asBoolean())
                    .as("第 " + (i + 2) + " 次确认应返回幂等标记").isTrue();
        }
        assertThat(offlineConfirmAuditCount()).as("重复确认不得新增审计").isEqualTo(auditTotalAfterHappy);
        assertSingleSideEffects(baseBooking);
    }

    @Test
    @Order(22)
    @DisplayName("相同幂等键重放：返回逐字节一致的响应，结算仍为 1")
    void sameIdempotencyKeyReplay() throws Exception {
        var order = createPendingOrder("幂等键重放");
        UUID orderId = UUID.fromString(order.get("orderId").asText());
        String key = UUID.randomUUID().toString();
        var first = rawPost(confirmUrl(orderId), tAdmin, null, key);
        assertThat(first.getResponse().getStatus()).isEqualTo(200);
        String firstBody = first.getResponse().getContentAsString();
        var replay = rawPost(confirmUrl(orderId), tAdmin, null, key);
        assertThat(replay.getResponse().getContentAsString()).as("幂等重放必须返回同一响应").isEqualTo(firstBody);
        assertThat(count("SELECT count(*) FROM settlement_entries WHERE booking_id=?",
                UUID.fromString(order.get("bookingId").asText()))).isEqualTo(1);
        // 本次合法首确使审计 +1：更新基线，保证后续测试断言语义正确
        auditTotalAfterHappy = offlineConfirmAuditCount();
    }

    @Test
    @Order(23)
    @DisplayName("连点 5 次 + 第二个运营账号（运营B）重复确认：副作用零增长")
    void rapidClicksAndSecondAdmin() throws Exception {
        for (int i = 0; i < 5; i++) {
            rawPost(confirmUrl(baseOrder), tAdmin, null, UUID.randomUUID().toString());
        }
        var result = rawPost(confirmUrl(baseOrder), tAdmin2, null, UUID.randomUUID().toString());
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(om.readTree(result.getResponse().getContentAsString()).get("idempotent").asBoolean()).isTrue();
        assertThat(offlineConfirmAuditCount()).isEqualTo(auditTotalAfterHappy);
        assertSingleSideEffects(baseBooking);
    }

    // ================================================================ 非法状态迁移

    @Test
    @Order(30)
    @DisplayName("CANCELLED 订单确认被拒：订单/支付/结算均不变")
    void cancelledOrderRejected() throws Exception {
        var order = createPendingOrder("取消态矩阵");
        UUID orderId = UUID.fromString(order.get("orderId").asText());
        UUID bookingId = UUID.fromString(order.get("bookingId").asText());
        var preview = Support.postJson(mvc, om, "/api/v1/booking/bookings/" + bookingId + ":cancel/preview",
                tStudent, new java.util.HashMap<>() {{ put("reason", "状态机测试"); }});
        Support.postJson(mvc, om, "/api/v1/booking/bookings/" + bookingId + ":cancel/execute", tStudent,
                new java.util.HashMap<>() {{
                    put("reason", "状态机测试");
                    put("confirmationToken", preview.get("confirmationToken").asText());
                }});
        assertThat(orderStatus(orderId)).isEqualTo("CANCELLED");

        var result = rawPost(confirmUrl(orderId), tAdmin, null, UUID.randomUUID().toString());
        assertThat(result.getResponse().getStatus()).as("CANCELLED 不可确认收款").isEqualTo(409);
        assertThat(orderStatus(orderId)).isEqualTo("CANCELLED");
        assertThat(count("SELECT count(*) FROM payment_attempts WHERE order_id=? AND status='SUCCEEDED'",
                orderId)).isZero();
        assertThat(count("SELECT count(*) FROM settlement_entries WHERE booking_id=?", bookingId)).isZero();
        assertThat(offlineConfirmAuditCount()).isEqualTo(auditTotalAfterHappy);
    }

    @Test
    @Order(31)
    @DisplayName("不存在的订单 → 400 订单不存在")
    void unknownOrderRejected() throws Exception {
        var result = rawPost(confirmUrl(UUID.randomUUID()), tAdmin, null, UUID.randomUUID().toString());
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(result.getResponse().getContentAsString()).contains("订单不存在");
    }

    @Test
    @Order(32)
    @DisplayName("金额不可篡改：请求体伪造金额被忽略，响应与结算均以服务端订单为准")
    void amountTamperIgnored() throws Exception {
        long serverAmount = jdbc.queryForObject(
                "SELECT amount_cents FROM orders WHERE id=?", Long.class, baseOrder);
        var result = rawPost(confirmUrl(baseOrder), tAdmin,
                new java.util.HashMap<>() {{ put("amountCents", 1); }}, UUID.randomUUID().toString());
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = om.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("amountCents").asLong()).as("不得采用请求伪造值").isNotEqualTo(1);
        assertThat(body.get("amountCents").asLong()).isEqualTo(serverAmount);
        assertThat(jdbc.queryForObject("SELECT gross_cents FROM settlement_entries WHERE booking_id=?",
                Long.class, baseBooking)).isEqualTo(serverAmount);
        assertThat(offlineConfirmAuditCount()).isEqualTo(auditTotalAfterHappy);
    }

    // ================================================================ 副作用单次性断言

    private void assertSingleSideEffects(UUID bookingId) {
        assertThat(count("SELECT count(*) FROM settlement_entries WHERE booking_id=?", bookingId))
                .as("结算必须恰好 1 条（ux_settlement_booking 兜底）").isEqualTo(1);
        assertThat(count("SELECT count(*) FROM payment_attempts pa JOIN orders o ON o.id=pa.order_id "
                + "WHERE o.booking_id=? AND pa.status='SUCCEEDED' AND pa.channel='OFFLINE'", bookingId))
                .as("成功支付记录必须恰好 1 条").isEqualTo(1);
        assertThat(count("SELECT count(*) FROM system_notices sn JOIN orders o ON "
                        + "(sn.recipient_id=o.teacher_id OR sn.recipient_id=o.payer_parent_id) "
                        + "WHERE o.booking_id=? AND sn.notice_type IN ('SETTLEMENT','PAYMENT') "
                        + "AND sn.body LIKE '%'||o.order_no||'%'",
                bookingId))
                .as("支付成功通知（导师+家长）必须恰好 2 条").isEqualTo(2);
    }

    private String confirmUrl(UUID orderId) {
        return "/api/v1/admin/orders/" + orderId + ":confirm-offline-payment";
    }
}
