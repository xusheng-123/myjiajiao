package com.nurture.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * G7 交易闭环集成回归（真 PostgreSQL）：可下单课程视图、学生/家长下单、
 * 付款方隔离、沙箱支付成功联动与钱包余额不足回滚、重复支付防护。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("it")
@Tag("integration")
class OrderPaymentFlowIT {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    private final Random random = new Random();

    private String phone() {
        return "13" + String.format("%09d", random.nextInt(1_000_000_000));
    }

    /** 已确认正式课（FORMAL + 导师审批）：付费订单唯一合法入口（试听免费规则 2026-10-10）。 */
    private String bookConfirmedFormal(com.nurture.it.Support.Account teacher, com.nurture.it.Support.Account student,
                                       String startAt) throws Exception {
        var slot = Support.postJson(mvc, om, "/api/v1/scheduling/availability-slots", teacher.accessToken(),
                new java.util.HashMap<>() {{
                    put("startAt", startAt);
                    put("endAt", startAt.replace("T10:00", "T11:00"));
                }});
        var booking = Support.postJson(mvc, om, "/api/v1/booking/bookings", student.accessToken(),
                new java.util.HashMap<>() {{
                    put("teacherId", teacher.accountId());
                    put("slotId", slot.get("slotId").asText());
                    put("subject", "物理");
                    put("bookingType", "FORMAL");
                }});
        assertThat(booking.get("status").asText()).isEqualTo("REQUESTED");
        var decided = Support.postJson(mvc, om,
                "/api/v1/booking/booking-requests/%s:decide?decision=approve".formatted(booking.get("bookingId").asText()),
                teacher.accessToken(), new java.util.HashMap<>());
        assertThat(decided.get("status").asText()).isEqualTo("CONFIRMED");
        return booking.get("bookingId").asText();
    }

    private JsonNode createOrder(String token, String bookingId) throws Exception {
        return Support.postJson(mvc, om, "/api/v1/order/orders", token,
                new java.util.HashMap<>() {{ put("bookingId", bookingId); }});
    }

    @Test
    void 可下单课程视图_学生与家长下单_支付联动与防护() throws Exception {
        var teacher = Support.register(mvc, om, "teacher", "TEACHER", phone(), "ItTeacher#1");
        var student = Support.register(mvc, om, "student", "STUDENT", phone(), "ItStudent#1");
        var parent = Support.register(mvc, om, "parent", "PARENT", phone(), "ItParent#01");
        var outsiderParent = Support.register(mvc, om, "parent", "PARENT", phone(), "ItParent#02");

        // 绑定 → ACTIVE
        var studentNo = Support.getJson(mvc, om, "/api/v1/student/me/profile", student.accessToken())
                .get("studentNo").asText();
        Support.postJson(mvc, om, "/api/v1/parent/me/children", parent.accessToken(),
                new java.util.HashMap<>() {{ put("studentNo", studentNo); put("relation", "父亲"); }});
        Support.postJson(mvc, om, "/api/v1/parent/me/children:confirm", student.accessToken(), new java.util.HashMap<>());
        var kids = Support.getJson(mvc, om, "/api/v1/parent/me/children", parent.accessToken());
        String studentProfileId = kids.get(0).get("studentId").asText();

        // 两节已确认课程 → 可下单视图含两节，金额为服务端定价（>0）
        String booking1 = bookConfirmedFormal(teacher, student, "2027-07-01T10:00:00+08:00");
        String booking2 = bookConfirmedFormal(teacher, student, "2027-07-02T10:00:00+08:00");
        var orderable = Support.getJson(mvc, om,
                "/api/v1/order/bookings:orderable?studentId=" + studentProfileId, parent.accessToken());
        assertThat(orderable.isArray()).isTrue();
        assertThat(orderable.size()).isEqualTo(2);
        assertThat(orderable.get(0).get("priceCents").asLong()).isPositive();
        assertThat(orderable.get(0).has("bookingId")).isTrue();

        // 越权：无关家长 → 403；学生本人 → 403
        mvc.perform(get("/api/v1/order/bookings:orderable?studentId=" + studentProfileId)
                        .header("Authorization", "Bearer " + outsiderParent.accessToken()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/order/bookings:orderable?studentId=" + studentProfileId)
                        .header("Authorization", "Bearer " + student.accessToken()))
                .andExpect(status().isForbidden());

        // 学生本人下单 → 201；付款方视角学生可见、家长不可见（payer 隔离）
        var studentOrder = createOrder(student.accessToken(), booking1);
        assertThat(studentOrder.get("orderId").asText()).isNotBlank();
        assertThat(studentOrder.get("status").asText()).isEqualTo("PENDING_PAYMENT");
        var studentOrders = Support.getJson(mvc, om, "/api/v1/order/me/orders?page=0&size=20",
                student.accessToken());
        assertThat(studentOrders.get("content").toString()).contains(booking1);
        var parentOrdersBefore = Support.getJson(mvc, om, "/api/v1/order/me/orders?page=0&size=20",
                parent.accessToken());
        assertThat(parentOrdersBefore.get("content").toString()).doesNotContain(booking1);

        // 一课一单：学生重复下单 → 409；家长可下单视图排除已有订单的课程
        mvc.perform(post("/api/v1/order/orders")
                        .header("Authorization", "Bearer " + student.accessToken())
                        .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsBytes(new java.util.HashMap<>() {{ put("bookingId", booking1); }})))
                .andExpect(status().isConflict());
        var orderableAfter = Support.getJson(mvc, om,
                "/api/v1/order/bookings:orderable?studentId=" + studentProfileId, parent.accessToken());
        assertThat(orderableAfter.size()).isEqualTo(1);
        assertThat(orderableAfter.get(0).get("bookingId").asText()).isEqualTo(booking2);

        // 支付能力配置（认证后可见；未认证访问被拒；沙箱开关为布尔字段）
        mvc.perform(get("/api/v1/payment/config"))
                .andExpect(status().is4xxClientError());
        var payCfg = Support.getJson(mvc, om, "/api/v1/payment/config", parent.accessToken());
        assertThat(payCfg.has("sandboxEnabled")).isTrue();

        // 学生为其订单发起沙箱支付：钱包无余额 → 支付联动失败（业务冲突），订单保持待支付（回滚保护）
        String studentOrderId = studentOrder.get("orderId").asText();
        var sInit = Support.postJson(mvc, om, "/api/v1/payment/payments:initiate", student.accessToken(),
                new java.util.HashMap<>() {{ put("orderId", studentOrderId); put("channel", "MOCK"); }});
        assertThat(sInit.get("attemptId").asText()).isNotBlank();
        mvc.perform(post("/api/v1/payment/sandbox/notify")
                        .header("Authorization", "Bearer " + student.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsBytes(new java.util.HashMap<>() {{
                            put("orderId", studentOrderId);
                            put("channel", "MOCK");
                            put("success", true);
                        }})))
                .andExpect(status().is4xxClientError());
        var sOrderAfter = Support.getJson(mvc, om, "/api/v1/order/orders/" + studentOrderId, student.accessToken());
        assertThat(sOrderAfter.get("status").asText()).isEqualTo("PENDING_PAYMENT");
        // 失败回滚后尝试仍为 INITIATED → 重复发起被拒（防重入）
        mvc.perform(post("/api/v1/payment/payments:initiate")
                        .header("Authorization", "Bearer " + student.accessToken())
                        .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsBytes(new java.util.HashMap<>() {{
                            put("orderId", studentOrderId);
                            put("channel", "MOCK");
                        }})))
                .andExpect(status().is4xxClientError());

        // 家长充值 → 家长下单 booking2 → 沙箱支付成功 → PAID + 钱包扣减
        Support.postJson(mvc, om, "/api/v1/parent/me/wallet/topup", parent.accessToken(),
                new java.util.HashMap<>() {{ put("amountCents", 100000); put("memo", "家庭充值"); }});
        var parentOrder = createOrder(parent.accessToken(), booking2);
        String parentOrderId = parentOrder.get("orderId").asText();
        long priceCents = parentOrder.get("amountCents").asLong();
        assertThat(priceCents).isPositive();
        Support.postJson(mvc, om, "/api/v1/payment/payments:initiate", parent.accessToken(),
                new java.util.HashMap<>() {{ put("orderId", parentOrderId); put("channel", "MOCK"); }});
        var notify = Support.postJson(mvc, om, "/api/v1/payment/sandbox/notify", parent.accessToken(),
                new java.util.HashMap<>() {{
                    put("orderId", parentOrderId);
                    put("channel", "MOCK");
                    put("success", true);
                }});
        assertThat(notify.get("result").asText()).isEqualTo("SUCCESS");
        var paidOrder = Support.getJson(mvc, om, "/api/v1/order/orders/" + parentOrderId, parent.accessToken());
        assertThat(paidOrder.get("status").asText()).isEqualTo("PAID");
        var wallet = Support.getJson(mvc, om, "/api/v1/parent/me/wallet", parent.accessToken());
        assertThat(wallet.get("balanceCents").asLong()).isEqualTo(100000 - priceCents);
        // 已支付订单不可再次发起支付（状态机防护）
        mvc.perform(post("/api/v1/payment/payments:initiate")
                        .header("Authorization", "Bearer " + parent.accessToken())
                        .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsBytes(new java.util.HashMap<>() {{
                            put("orderId", parentOrderId);
                            put("channel", "MOCK");
                        }})))
                .andExpect(status().is4xxClientError());
    }
}
