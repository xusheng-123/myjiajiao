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
 * D11 集成回归（真 PostgreSQL）：改期成功切换档期、无关用户 403、
 * 同档期 409、取消后状态机拒绝、档期被他人占用 409 且原档期保留。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("it")
@Tag("integration")
class BookingRescheduleIT {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    private final Random random = new Random();

    private String phone() {
        return "15" + String.format("%09d", random.nextInt(1_000_000_000));
    }

    private String publishSlot(String token, String day, String hour) throws Exception {
        var resp = mvc.perform(post("/api/v1/scheduling/availability-slots")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsBytes(new HashMap<String, String>() {{
                            put("startAt", "2027-07-%sT%s:00:00+08:00".formatted(day, hour));
                            put("endAt", "2027-07-%sT%s:00:00+08:00".formatted(day, String.valueOf(Integer.parseInt(hour) + 1)));
                        }})))
                .andExpect(status().is2xxSuccessful()).andReturn();
        return om.readTree(resp.getResponse().getContentAsString()).get("slotId").asText();
    }

    private String bookTrial(String token, java.util.UUID teacherId, String slotId) throws Exception {
        var resp = mvc.perform(post("/api/v1/booking/bookings")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsBytes(new HashMap<String, Object>() {{
                            put("teacherId", teacherId);
                            put("slotId", slotId);
                            put("subject", "物理");
                            put("bookingType", "TRIAL");
                        }})))
                .andExpect(status().isCreated()).andReturn();
        return om.readTree(resp.getResponse().getContentAsString()).get("bookingId").asText();
    }

    @Test
    void 改期全链路_含状态机拒绝() throws Exception {
        var teacher = Support.register(mvc, om, "teacher", "TEACHER", phone(), "ItTeacher#1");
        var student = Support.register(mvc, om, "student", "STUDENT", phone(), "ItStudent#1");
        var outsider = Support.register(mvc, om, "student", "STUDENT", phone(), "ItStudent#2");

        String slot1 = publishSlot(teacher.accessToken(), "01", "10");
        String slot2 = publishSlot(teacher.accessToken(), "02", "10");

        String bookingId = bookTrial(student.accessToken(), teacher.accountId(), slot1);

        // 无关用户改期：403
        mvc.perform(post("/api/v1/booking/bookings/%s:reschedule".formatted(bookingId))
                        .header("Authorization", "Bearer " + outsider.accessToken())
                        .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsBytes(new HashMap<>() {{ put("newSlotId", slot2); }})))
                .andExpect(status().isForbidden());

        // 同档期改期：409
        mvc.perform(post("/api/v1/booking/bookings/%s:reschedule".formatted(bookingId))
                        .header("Authorization", "Bearer " + student.accessToken())
                        .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsBytes(new HashMap<>() {{ put("newSlotId", slot1); }})))
                .andExpect(status().isConflict());

        // 正常改期到 slot2：200 且档期切换
        var rescheduled = mvc.perform(post("/api/v1/booking/bookings/%s:reschedule".formatted(bookingId))
                        .header("Authorization", "Bearer " + student.accessToken())
                        .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsBytes(new HashMap<>() {{ put("newSlotId", slot2); }})))
                .andExpect(status().isOk()).andReturn();
        assertThat(om.readTree(rescheduled.getResponse().getContentAsString()).get("slotId").asText())
                .isEqualTo(slot2);

        // 取消后再改期：409（状态机拒绝）
        var preview = mvc.perform(post("/api/v1/booking/bookings/%s:cancel/preview".formatted(bookingId))
                        .header("Authorization", "Bearer " + student.accessToken())
                        .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsBytes(new HashMap<>() {{ put("reason", "集成测试取消"); }})))
                .andExpect(status().isOk()).andReturn();
        String confirmationToken = om.readTree(preview.getResponse().getContentAsString())
                .get("confirmationToken").asText();
        mvc.perform(post("/api/v1/booking/bookings/%s:cancel/execute".formatted(bookingId))
                        .header("Authorization", "Bearer " + student.accessToken())
                        .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsBytes(new HashMap<>() {{
                            put("reason", "集成测试取消");
                            put("confirmationToken", confirmationToken);
                        }})))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/booking/bookings/%s:reschedule".formatted(bookingId))
                        .header("Authorization", "Bearer " + student.accessToken())
                        .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsBytes(new HashMap<>() {{ put("newSlotId", slot1); }})))
                .andExpect(status().isConflict());
    }

    @Test
    void 改期到已被其他预约占用的档期_冲突409且原档期保留() throws Exception {
        var teacher = Support.register(mvc, om, "teacher", "TEACHER", phone(), "ItTeacher#1");
        var s1 = Support.register(mvc, om, "student", "STUDENT", phone(), "ItStudent#1");
        var s2 = Support.register(mvc, om, "student", "STUDENT", phone(), "ItStudent#2");

        String slot1 = publishSlot(teacher.accessToken(), "05", "10");
        String slot2 = publishSlot(teacher.accessToken(), "06", "10");

        String b1 = bookTrial(s1.accessToken(), teacher.accountId(), slot1);
        String b2 = bookTrial(s2.accessToken(), teacher.accountId(), slot2);

        // 学生1 的预约改到 slot2（已被学生2 占用）→ 冲突 409
        mvc.perform(post("/api/v1/booking/bookings/%s:reschedule".formatted(b1))
                        .header("Authorization", "Bearer " + s1.accessToken())
                        .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsBytes(new HashMap<>() {{ put("newSlotId", slot2); }})))
                .andExpect(status().isConflict());

        // 原档期保留（原子回滚证明）
        var detail = mvc.perform(get("/api/v1/booking/bookings/" + b1)
                        .header("Authorization", "Bearer " + s1.accessToken()))
                .andExpect(status().isOk()).andReturn();
        assertThat(om.readTree(detail.getResponse().getContentAsString()).get("slotId").asText())
                .isEqualTo(slot1);
    }
}
