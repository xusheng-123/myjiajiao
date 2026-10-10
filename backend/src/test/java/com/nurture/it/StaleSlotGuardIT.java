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
 * 质量加固回归（真 PostgreSQL）：过期档期防护——
 * ① 过去时间的 OPEN 档期不出现在公开可约列表；② 对过去档期新建预约被服务端拒绝；
 * ③ 未来有效档期正常展示与预约；④ 历史/日历记录不被物理删除（教师日历仍可见过去档期）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("it")
@Tag("integration")
class StaleSlotGuardIT {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    private final Random random = new Random();

    private String phone() {
        return "16" + String.format("%09d", random.nextInt(1_000_000_000));
    }

    private String at(long hoursAhead) {
        return OffsetDateTime.now().plusHours(hoursAhead).withNano(0).toString();
    }

    private String publishSlot(String teacherToken, long startHour, long endHour) throws Exception {
        var slot = Support.postJson(mvc, om, "/api/v1/scheduling/availability-slots", teacherToken,
                new java.util.HashMap<>() {{
                    put("startAt", at(startHour));
                    put("endAt", at(endHour));
                }});
        assertThat(slot.has("slotId")).isTrue();
        return slot.get("slotId").asText();
    }

    @Test
    void pastOpenSlot_hiddenFromOpenList_andBookingRejected() throws Exception {
        var teacher = Support.register(mvc, om, "teacher", "TEACHER",
                phone(), "It#Teacher2026");
        var student = Support.register(mvc, om, "student", "STUDENT",
                phone(), "It#Student2026");

        // 发布接口允许创建过去档期（与线上历史残留同源），用于验证下游防护
        String pastSlot = publishSlot(teacher.accessToken(), -3, -2);
        String futureSlot = publishSlot(teacher.accessToken(), 50, 51);

        // ① 可约列表（学生视角）：过去档期不可见，未来档期正常
        JsonNode openSlots = Support.getJson(mvc, om,
                "/api/v1/scheduling/teachers/" + teacher.accountId() + "/open-slots", student.accessToken());
        var ids = new java.util.HashSet<String>();
        openSlots.forEach(s -> ids.add(s.get("slotId").asText()));
        assertThat(ids).contains(futureSlot).doesNotContain(pastSlot);

        // ② 对过去档期新建预约：服务端拒绝（400），且档期不被真正持有
        mvc.perform(post("/api/v1/booking/bookings")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsBytes(new java.util.HashMap<>() {{
                            put("teacherId", teacher.accountId());
                            put("slotId", pastSlot);
                            put("subject", "数学");
                            put("bookingType", "TRIAL");
                        }}))
                        .header("Authorization", "Bearer " + student.accessToken())
                        .header("Idempotency-Key", java.util.UUID.randomUUID().toString()))
                .andExpect(status().isBadRequest());

        // ③ 未来档期预约仍正常
        var booking = Support.postJson(mvc, om, "/api/v1/booking/bookings", student.accessToken(),
                new java.util.HashMap<>() {{
                    put("teacherId", teacher.accountId());
                    put("slotId", futureSlot);
                    put("subject", "数学");
                    put("bookingType", "TRIAL");
                }});
        assertThat(booking.get("status").asText()).isEqualTo("CONFIRMED");

        // ④ 历史记录不删除：教师日历（宽区间）仍能看到过去档期，可自行管理
        var calendar = Support.getJson(mvc, om,
                "/api/v1/scheduling/me/calendar?from=" + at(-24 * 30) + "&to=" + at(24 * 30),
                teacher.accessToken());
        boolean pastStillListed = false;
        if (calendar.isArray()) {
            for (JsonNode s : calendar) {
                if (pastSlot.equals(s.get("slotId").asText())) pastStillListed = true;
            }
        }
        assertThat(pastStillListed).isTrue();
    }
}
