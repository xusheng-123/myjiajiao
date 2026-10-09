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

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * D1 核心集成回归（真 PostgreSQL）：家长-学生监护绑定必须参与交易授权——
 * 未绑定家长/无关学生不可为他人预约下单；绑定确认后家长可下单；一课一单保持。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("it")
@Tag("integration")
class GuardianBindingOrderAuthIT {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    private final Random random = new Random();

    private String phone() {
        return "13" + String.format("%09d", random.nextInt(1_000_000_000));
    }

    @Test
    void 监护绑定未确认前家长不可下单_确认后可下单() throws Exception {
        var teacher = Support.register(mvc, om, "teacher", "TEACHER", phone(), "ItTeacher#1");
        var student = Support.register(mvc, om, "student", "STUDENT", phone(), "ItStudent#1");
        var parent = Support.register(mvc, om, "parent", "PARENT", phone(), "ItParent#01");
        var outsider = Support.register(mvc, om, "student", "STUDENT", phone(), "ItStudent#2");

        // 教师发布档期 → 学生预约试听（自动确认）
        var slot = Support.postJson(mvc, om, "/api/v1/scheduling/availability-slots",
                teacher.accessToken(),
                new java.util.HashMap<>() {{
                    put("startAt", "2027-06-01T10:00:00+08:00");
                    put("endAt", "2027-06-01T11:00:00+08:00");
                }});
        assertThat(slot.get("slotId").asText()).isNotBlank();

        var booking = Support.postJson(mvc, om, "/api/v1/booking/bookings", student.accessToken(),
                new java.util.HashMap<>() {{
                    put("teacherId", teacher.accountId());
                    put("slotId", slot.get("slotId").asText());
                    put("subject", "数学");
                    put("bookingType", "TRIAL");
                }});
        assertThat(booking.get("status").asText()).isEqualTo("CONFIRMED");
        String bookingId = booking.get("bookingId").asText();

        // 无关学生：403
        Support.postJson(mvc, om, "/api/v1/order/orders", outsider.accessToken(),
                        new java.util.HashMap<>() {{ put("bookingId", bookingId); }})
                .isEmpty();
        var mvcResult = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/order/orders")
                        .header("Authorization", "Bearer " + outsider.accessToken())
                        .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsBytes(new java.util.HashMap<>() {{ put("bookingId", bookingId); }})))
                .andExpect(status().isForbidden()).andReturn();
        assertThat(mvcResult.getResponse().getContentAsString()).contains("监护家长可下单");

        // 未绑定家长：403
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/order/orders")
                        .header("Authorization", "Bearer " + parent.accessToken())
                        .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsBytes(new java.util.HashMap<>() {{ put("bookingId", bookingId); }})))
                .andExpect(status().isForbidden());

        // 家长发起绑定 → 学生确认 → ACTIVE
        var studentNo = Support.getJson(mvc, om, "/api/v1/student/me/profile", student.accessToken())
                .get("studentNo").asText();
        var bindResp = Support.postJson(mvc, om, "/api/v1/parent/me/children", parent.accessToken(),
                new java.util.HashMap<>() {{ put("studentNo", studentNo); put("relation", "母亲"); }});
        assertThat(bindResp.get("status").asText()).isIn("PENDING", "ACTIVE");
        // 学生侧确认绑定（接口已迁移至 parent 模块：children:confirm，学生身份调用）
        Support.postJson(mvc, om, "/api/v1/parent/me/children:confirm", student.accessToken(), new java.util.HashMap<>());

        // 绑定确认后：家长可下单
        var order = Support.postJson(mvc, om, "/api/v1/order/orders", parent.accessToken(),
                new java.util.HashMap<>() {{ put("bookingId", bookingId); }});
        assertThat(order.get("orderId").asText()).isNotBlank();

        // 一课一单：学生本人再下单 → 409
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/order/orders")
                        .header("Authorization", "Bearer " + student.accessToken())
                        .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsBytes(new java.util.HashMap<>() {{ put("bookingId", bookingId); }})))
                .andExpect(status().isConflict());
    }
}
