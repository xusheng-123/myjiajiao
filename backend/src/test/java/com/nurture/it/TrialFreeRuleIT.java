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
 * 试听免费规则集成回归（2026-10-10 决策，真 PostgreSQL）：
 * TRIAL 预约不得创建付费订单（学生/家长均 409）、不出现在可下单视图、
 * 无订单取消的合法态保留；FORMAL 下单/定价快照/越权防护不回归。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("it")
@Tag("integration")
class TrialFreeRuleIT {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    private final Random random = new Random();

    private String phone() {
        return "16" + String.format("%09d", random.nextInt(1_000_000_000));
    }

    /** 试听预约（自动确认，保持试听语义）。 */
    private String bookTrial(Support.Account teacher, Support.Account student, String startAt) throws Exception {
        var slot = Support.postJson(mvc, om, "/api/v1/scheduling/availability-slots", teacher.accessToken(),
                new HashMap<>() {{
                    put("startAt", startAt);
                    put("endAt", startAt.replace("T10:00", "T11:00"));
                }});
        var booking = Support.postJson(mvc, om, "/api/v1/booking/bookings", student.accessToken(),
                new HashMap<>() {{
                    put("teacherId", teacher.accountId());
                    put("slotId", slot.get("slotId").asText());
                    put("subject", "物理");
                    put("bookingType", "TRIAL");
                }});
        assertThat(booking.get("status").asText()).isEqualTo("CONFIRMED");
        return booking.get("bookingId").asText();
    }

    /** 正式课预约 + 导师审批（付费订单唯一合法入口）。 */
    private String bookFormalApproved(Support.Account teacher, Support.Account student, String startAt) throws Exception {
        var slot = Support.postJson(mvc, om, "/api/v1/scheduling/availability-slots", teacher.accessToken(),
                new HashMap<>() {{
                    put("startAt", startAt);
                    put("endAt", startAt.replace("T10:00", "T11:00"));
                }});
        var booking = Support.postJson(mvc, om, "/api/v1/booking/bookings", student.accessToken(),
                new HashMap<>() {{
                    put("teacherId", teacher.accountId());
                    put("slotId", slot.get("slotId").asText());
                    put("subject", "物理");
                    put("bookingType", "FORMAL");
                }});
        assertThat(booking.get("status").asText()).isEqualTo("REQUESTED");
        var decided = Support.postJson(mvc, om,
                "/api/v1/booking/booking-requests/%s:decide?decision=approve".formatted(booking.get("bookingId").asText()),
                teacher.accessToken(), new HashMap<>());
        assertThat(decided.get("status").asText()).isEqualTo("CONFIRMED");
        return booking.get("bookingId").asText();
    }

    @Test
    void 试听预约不得创建付费订单且不出现在可下单视图() throws Exception {
        var teacher = Support.register(mvc, om, "teacher", "TEACHER", phone(), "ItTeacher#1");
        var student = Support.register(mvc, om, "student", "STUDENT", phone(), "ItStudent#1");
        var parent = Support.register(mvc, om, "parent", "PARENT", phone(), "ItParent#01");
        var outsider = Support.register(mvc, om, "student", "STUDENT", phone(), "ItStudent#2");

        // 教师设一门在售定价（¥200/小时），供 FORMAL 定价快照断言
        var offering = Support.postJson(mvc, om, "/api/v1/teacher/me/subjects", teacher.accessToken(),
                new HashMap<>() {{
                    put("subject", "物理");
                    put("gradeBand", "高中");
                    put("pricePerHourCents", 20000);
                }});
        assertThat(offering.get("offeringId").asText()).isNotBlank();

        // 绑定 → ACTIVE（家长可下单前置）
        var studentNo = Support.getJson(mvc, om, "/api/v1/student/me/profile", student.accessToken())
                .get("studentNo").asText();
        Support.postJson(mvc, om, "/api/v1/parent/me/children", parent.accessToken(),
                new HashMap<>() {{ put("studentNo", studentNo); put("relation", "母亲"); }});
        Support.postJson(mvc, om, "/api/v1/parent/me/children:confirm", student.accessToken(), new HashMap<>());
        var kids = Support.getJson(mvc, om, "/api/v1/parent/me/children", parent.accessToken());
        String studentProfileId = kids.get(0).get("studentId").asText();

        String trialBooking = bookTrial(teacher, student, "2027-08-01T10:00:00+08:00");
        String formalBooking = bookFormalApproved(teacher, student, "2027-08-02T10:00:00+08:00");

        // 1+3：学生本人与监护家长对 TRIAL 下单均被拒（409，明确业务语义）
        mvc.perform(post("/api/v1/order/orders")
                        .header("Authorization", "Bearer " + student.accessToken())
                        .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsBytes(new HashMap<>() {{ put("bookingId", trialBooking); }})))
                .andExpect(status().isConflict())
                .andExpect(r -> assertThat(r.getResponse().getContentAsString()).contains("试听"));
        mvc.perform(post("/api/v1/order/orders")
                        .header("Authorization", "Bearer " + parent.accessToken())
                        .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsBytes(new HashMap<>() {{ put("bookingId", trialBooking); }})))
                .andExpect(status().isConflict());

        // 2：可下单视图只含 FORMAL，不含 TRIAL（服务端过滤，非前端隐藏）
        var orderable = Support.getJson(mvc, om,
                "/api/v1/order/bookings:orderable?studentId=" + studentProfileId, parent.accessToken());
        assertThat(orderable.isArray()).isTrue();
        assertThat(orderable.size()).isEqualTo(1);
        assertThat(orderable.get(0).get("bookingId").asText()).isEqualTo(formalBooking);
        assertThat(orderable.get(0).get("type").asText()).isEqualTo("FORMAL");

        // 4+5：FORMAL 正常下单，金额=在售定价快照
        var order = Support.postJson(mvc, om, "/api/v1/order/orders", parent.accessToken(),
                new HashMap<>() {{ put("bookingId", formalBooking); }});
        assertThat(order.get("status").asText()).isEqualTo("PENDING_PAYMENT");
        assertThat(order.get("amountCents").asLong()).isEqualTo(20000L);
        String formalOrderId = order.get("orderId").asText();

        // 5（续）：调价后既有订单快照不变
        String offeringId = offering.get("offeringId").asText();
        Support.postJson(mvc, om, "/api/v1/teacher/me/subjects/%s/pricing".formatted(offeringId),
                teacher.accessToken(), new HashMap<>() {{ put("pricePerHourCents", 30000); }});
        var orderAfter = Support.getJson(mvc, om, "/api/v1/order/orders/" + formalOrderId, parent.accessToken());
        assertThat(orderAfter.get("amountCents").asLong()).isEqualTo(20000L);

        // 7：未授权第三方不能创建/读取他人订单
        mvc.perform(post("/api/v1/order/orders")
                        .header("Authorization", "Bearer " + outsider.accessToken())
                        .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsBytes(new HashMap<>() {{ put("bookingId", formalBooking); }})))
                .andExpect(status().is4xxClientError());
        mvc.perform(get("/api/v1/order/orders/" + formalOrderId)
                        .header("Authorization", "Bearer " + outsider.accessToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void 试听取消与结课合法流程不依赖订单() throws Exception {
        var teacher = Support.register(mvc, om, "teacher", "TEACHER", phone(), "ItTeacher#1");
        var student = Support.register(mvc, om, "student", "STUDENT", phone(), "ItStudent#1");

        // 无订单试听的两阶段取消：Preview 显示无退款（NO_ORDER），Execute 正常终态
        String trial = bookTrial(teacher, student, "2027-08-03T10:00:00+08:00");
        var preview = Support.postJson(mvc, om,
                "/api/v1/booking/bookings/%s:cancel/preview".formatted(trial), student.accessToken(),
                new HashMap<>() {{ put("reason", "集成测试取消试听"); }});
        assertThat(preview.get("cascadeImpact").get("orderStatus").asText()).isEqualTo("NO_ORDER");
        assertThat(preview.get("cascadeImpact").get("refundAmountCents").asLong()).isZero();
        String token = preview.get("confirmationToken").asText();
        var executed = Support.postJson(mvc, om,
                "/api/v1/booking/bookings/%s:cancel/execute".formatted(trial), student.accessToken(),
                new HashMap<>() {{
                    put("reason", "集成测试取消试听");
                    put("confirmationToken", token);
                }});
        assertThat(executed.get("status").asText()).isEqualTo("CANCELLED");

        // 无订单试听的结课链路：开始 → 完成（考勤/评价前置不依赖订单）
        String trial2 = bookTrial(teacher, student, "2027-08-04T10:00:00+08:00");
        var started = Support.postJson(mvc, om, "/api/v1/booking/bookings/%s:start".formatted(trial2),
                teacher.accessToken(), new HashMap<>());
        assertThat(started.get("status").asText()).isEqualTo("IN_PROGRESS");
        var completed = Support.postJson(mvc, om, "/api/v1/booking/bookings/%s:complete".formatted(trial2),
                teacher.accessToken(), new HashMap<>());
        assertThat(completed.get("status").asText()).isEqualTo("COMPLETED");
    }
}
