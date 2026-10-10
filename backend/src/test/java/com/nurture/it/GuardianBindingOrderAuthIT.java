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

        // 教师发布档期 → 学生预约正式课 → 导师审批（付费订单合法入口；试听免费规则 2026-10-10）
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
                    put("bookingType", "FORMAL");
                }});
        assertThat(booking.get("status").asText()).isEqualTo("REQUESTED");
        var approved = Support.postJson(mvc, om,
                "/api/v1/booking/booking-requests/%s:decide?decision=approve".formatted(booking.get("bookingId").asText()),
                teacher.accessToken(), new java.util.HashMap<>());
        assertThat(approved.get("status").asText()).isEqualTo("CONFIRMED");
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

    /** G3：学生查看/确认本人的待确认监护绑定申请——对象级隔离与幂等。 */
    @Test
    void 学生待确认申请_仅本人可见_确认幂等且无越权副作用() throws Exception {
        var parent = Support.register(mvc, om, "parent", "PARENT", phone(), "ItParent#02");
        var studentA = Support.register(mvc, om, "student", "STUDENT", phone(), "ItStudent#3");
        var studentB = Support.register(mvc, om, "student", "STUDENT", phone(), "ItStudent#4");
        var teacher = Support.register(mvc, om, "teacher", "TEACHER", phone(), "ItTeacher#2");

        // 家长向 A 发起绑定
        var studentNoA = Support.getJson(mvc, om, "/api/v1/student/me/profile", studentA.accessToken())
                .get("studentNo").asText();
        var bind = Support.postJson(mvc, om, "/api/v1/parent/me/children", parent.accessToken(),
                new java.util.HashMap<>() {{ put("studentNo", studentNoA); put("relation", "父亲"); }});
        assertThat(bind.get("status").asText()).isEqualTo("PENDING");

        // A 可见本人申请（含申请方称呼），B 的列表为空（对象级隔离）
        var pendingA = Support.getJson(mvc, om, "/api/v1/parent/me/children:pending", studentA.accessToken());
        assertThat(pendingA.isArray()).isTrue();
        assertThat(pendingA.size()).isGreaterThanOrEqualTo(1);
        assertThat(pendingA.get(0).has("parentDisplayName")).isTrue();
        var pendingB = Support.getJson(mvc, om, "/api/v1/parent/me/children:pending", studentB.accessToken());
        assertThat(pendingB.isArray()).isTrue();
        assertThat(pendingB.size()).isEqualTo(0);

        // 教师无权访问学生侧申请列表（角色护栏）→ 403
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/parent/me/children:pending")
                        .header("Authorization", "Bearer " + teacher.accessToken()))
                .andExpect(status().isForbidden());

        // 无关学生 B 尝试确认：无待确认申请 → 业务拒绝，且不消耗 A 的申请
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/parent/me/children:confirm")
                        .header("Authorization", "Bearer " + studentB.accessToken())
                        .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().is4xxClientError());

        // A 确认 → ACTIVE；确认后列表清空；家长侧 children 出现 ACTIVE
        var confirmed = Support.postJson(mvc, om, "/api/v1/parent/me/children:confirm", studentA.accessToken(),
                new java.util.HashMap<>());
        assertThat(confirmed.get("status").asText()).isEqualTo("ACTIVE");
        var pendingAfter = Support.getJson(mvc, om, "/api/v1/parent/me/children:pending", studentA.accessToken());
        assertThat(pendingAfter.size()).isEqualTo(0);
        var kids = Support.getJson(mvc, om, "/api/v1/parent/me/children", parent.accessToken());
        assertThat(kids.isArray()).isTrue();
        assertThat(kids.size()).isGreaterThanOrEqualTo(1);

        // 重复确认：已无 PENDING → 业务拒绝，无重复副作用
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/parent/me/children:confirm")
                        .header("Authorization", "Bearer " + studentA.accessToken())
                        .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().is4xxClientError());
        // 家长侧 ACTIVE 恰好 1 条（无重复激活）
        var kidsAgain = Support.getJson(mvc, om, "/api/v1/parent/me/children", parent.accessToken());
        long activeCount = 0;
        for (var k : kidsAgain) {
            if ("ACTIVE".equals(k.get("status").asText())) {
                activeCount++;
            }
        }
        assertThat(activeCount).isEqualTo(1);
    }
}
