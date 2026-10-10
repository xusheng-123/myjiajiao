package com.nurture.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 质量加固回归（真 PostgreSQL）：下架课程与订单定价三分支——
 * ① 在售条目按真实定价下单；② 明确存在但已下架的科目拒绝新订单（不回落默认价）；
 * ③ 从未设置该科目定价时保留 ¥300 默认价兜底（合法场景不破坏）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("it")
@Tag("integration")
class DelistedCoursePricingIT {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    private final Random random = new Random();

    private String phone() {
        return "15" + String.format("%09d", random.nextInt(1_000_000_000));
    }

    private final java.util.concurrent.atomic.AtomicInteger slotOffset = new java.util.concurrent.atomic.AtomicInteger(72);

    private String futureAt(long hoursAhead) {
        return OffsetDateTime.now().plusHours(hoursAhead).withNano(0).toString();
    }

    private String publishSlot(String teacherToken) throws Exception {
        // 每次发布错开 3 小时，避免与同教师已有档期重叠（409）
        long base = slotOffset.getAndAdd(3);
        var slot = Support.postJson(mvc, om, "/api/v1/scheduling/availability-slots", teacherToken,
                new java.util.HashMap<>() {{
                    put("startAt", futureAt(base));
                    put("endAt", futureAt(base + 1));
                }});
        assertThat(slot.has("slotId")).isTrue();
        return slot.get("slotId").asText();
    }

    /** 正式课预约 → 教师审批 → 返回 bookingId（CONFIRMED）。 */
    private String confirmedFormalBooking(Support.Account teacher, Support.Account student,
                                          String slotId, String subject) throws Exception {
        var booking = Support.postJson(mvc, om, "/api/v1/booking/bookings", student.accessToken(),
                new java.util.HashMap<>() {{
                    put("teacherId", teacher.accountId());
                    put("slotId", slotId);
                    put("subject", subject);
                    put("bookingType", "FORMAL");
                }});
        assertThat(booking.get("status").asText()).isEqualTo("REQUESTED");
        mvc.perform(post("/api/v1/booking/booking-requests/" + booking.get("bookingId").asText() + ":decide?decision=approve")
                        .header("Authorization", "Bearer " + teacher.accessToken())
                        .header("Idempotency-Key", java.util.UUID.randomUUID().toString()))
                .andExpect(status().isOk());
        return booking.get("bookingId").asText();
    }

    private JsonNode createOrder(String token, String bookingId) throws Exception {
        return Support.postJson(mvc, om, "/api/v1/order/orders", token,
                new java.util.HashMap<>() {{ put("bookingId", bookingId); }});
    }

    @Test
    void delistedCourse_refusesNewOrder_neverFallsBackToDefaultPrice() throws Exception {
        var teacher = Support.register(mvc, om, "teacher", "TEACHER",
                phone(), "It#Teacher2026");
        var student = Support.register(mvc, om, "student", "STUDENT",
                phone(), "It#Student2026");

        // 在售化学条目 ¥180：FORMAL 预约 → 审批 → 下单按真实定价
        var offering = Support.postJson(mvc, om, "/api/v1/teacher/me/subjects", teacher.accessToken(),
                new java.util.HashMap<>() {{
                    put("subject", "化学");
                    put("gradeBand", "高一");
                    put("pricePerHourCents", 18000);
                }});
        assertThat(offering.get("offeringId").asText()).isNotBlank();

        var booking1 = confirmedFormalBooking(teacher, student, publishSlot(teacher.accessToken()), "化学");
        var order1 = createOrder(student.accessToken(), booking1);
        assertThat(order1.get("amountCents").asLong()).isEqualTo(18000);

        // 下架（停止销售）：新订单必须被拒绝，不得回落 ¥300
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/v1/teacher/me/subjects/" + offering.get("offeringId").asText())
                        .header("Authorization", "Bearer " + teacher.accessToken())
                        .header("Idempotency-Key", java.util.UUID.randomUUID().toString()))
                .andExpect(status().is(org.hamcrest.Matchers.anyOf(
                        org.hamcrest.Matchers.is(200), org.hamcrest.Matchers.is(204))));

        var booking2 = confirmedFormalBooking(teacher, student, publishSlot(teacher.accessToken()), "化学");
        mvc.perform(post("/api/v1/order/orders")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsBytes(new java.util.HashMap<>() {{ put("bookingId", booking2); }}))
                        .header("Authorization", "Bearer " + student.accessToken())
                        .header("Idempotency-Key", java.util.UUID.randomUUID().toString()))
                .andExpect(status().isConflict());

        // 教师从未设置「生物」定价：默认价兜底保留（合法场景）
        var booking3 = confirmedFormalBooking(teacher, student, publishSlot(teacher.accessToken()), "生物");
        var order3 = createOrder(student.accessToken(), booking3);
        assertThat(order3.get("amountCents").asLong()).isEqualTo(30000);

        // 已生成订单的历史价格不受后续下架影响（order1 快照仍 18000）
        var order1Again = Support.getJson(mvc, om, "/api/v1/order/orders/" + order1.get("orderId").asText(),
                student.accessToken());
        assertThat(order1Again.get("amountCents").asLong()).isEqualTo(18000);
    }
}
