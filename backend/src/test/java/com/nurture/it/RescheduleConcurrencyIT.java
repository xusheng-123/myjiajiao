package com.nurture.it;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Random;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 并发改期一致性集成回归（真 PostgreSQL，真实并发事务）：
 * 同一预约的两个并发改期请求竞争不同目标档期。
 * 档期实体 @Version 乐观锁 + 「先锁新、再释放旧」顺序应保证：
 * 无论请求如何交错（并发失败一方或顺序先后成功），最终预约只指向一个档期，
 * 其余相关档期全部回到 OPEN——不存在双重占用或孤儿 HELD。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("it")
@Tag("integration")
class RescheduleConcurrencyIT {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired com.nurture.scheduling.infra.AvailabilitySlotRepository slotRepository;
    private final Random random = new Random();

    private String phone() {
        return "18" + String.format("%09d", random.nextInt(1_000_000_000));
    }

    private OffsetDateTime at(long plusHours) {
        return OffsetDateTime.now().plusHours(plusHours).truncatedTo(java.time.temporal.ChronoUnit.HOURS);
    }

    private String publishSlot(Support.Account teacher, OffsetDateTime start) throws Exception {
        var slot = Support.postJson(mvc, om, "/api/v1/scheduling/availability-slots", teacher.accessToken(),
                new HashMap<>() {{
                    put("startAt", start.toString());
                    put("endAt", start.plusHours(1).toString());
                }});
        return slot.get("slotId").asText();
    }

    @RepeatedTest(3)
    void 同一预约并发改期_预约仅指向单档期且无孤儿HELD() throws Exception {
        var teacher = Support.register(mvc, om, "teacher", "TEACHER", phone(), "ItTeacher#1");
        var student = Support.register(mvc, om, "student", "STUDENT", phone(), "ItStudent#1");

        String slotA = publishSlot(teacher, at(49));
        String slotB = publishSlot(teacher, at(73));
        String slotC = publishSlot(teacher, at(97));

        // 试听预约（自动确认）占用 A
        var booking = Support.postJson(mvc, om, "/api/v1/booking/bookings", student.accessToken(),
                new HashMap<>() {{
                    put("teacherId", teacher.accountId());
                    put("slotId", slotA);
                    put("subject", "物理");
                    put("bookingType", "TRIAL");
                }});
        String bookingId = booking.get("bookingId").asText();

        // 两个真实并发事务：同一预约分别改期到 B 与 C
        CyclicBarrier barrier = new CyclicBarrier(2);
        int[] status = new int[2];
        Thread t1 = rescheduleThread(student.accessToken(), bookingId, slotB, barrier, status, 0);
        Thread t2 = rescheduleThread(student.accessToken(), bookingId, slotC, barrier, status, 1);
        t1.start();
        t2.start();
        t1.join(TimeUnit.SECONDS.toMillis(30));
        t2.join(TimeUnit.SECONDS.toMillis(30));
        assertThat(t1.isAlive()).isFalse();
        assertThat(t2.isAlive()).isFalse();

        int successes = (status[0] / 100 == 2 ? 1 : 0) + (status[1] / 100 == 2 ? 1 : 0);
        // 至少一个成功；若恰一成一败，败方须为 4xx/5xx 业务拒绝而非悬死
        assertThat(successes).isGreaterThanOrEqualTo(1);
        if (successes == 1) {
            int loser = status[0] / 100 == 2 ? status[1] : status[0];
            assertThat(loser).isGreaterThanOrEqualTo(400);
        }

        // 一致性不变量：预约只指向 B/C 中被改成的那个档期
        var detail = Support.getJson(mvc, om, "/api/v1/booking/bookings/" + bookingId, student.accessToken());
        String finalSlot = detail.get("slotId").asText();
        if (successes == 1) {
            assertThat(finalSlot).isIn(slotB, slotC);
        }

        // 核心断言（无孤儿）：A/B/C 三个档期中，仅预约指向的档期处于占用态（试听=HELD），
        // 其余全部回到 OPEN。若并发控制失效，将出现两个 HELD（孤儿占用）。
        int heldCount = 0;
        for (String id : new String[]{slotA, slotB, slotC}) {
            var slot = slotRepository.findById(java.util.UUID.fromString(id)).orElseThrow();
            switch (slot.getStatus().name()) {
                case "HELD", "BOOKED" -> {
                    heldCount++;
                    assertThat(id).isEqualTo(finalSlot);
                }
                case "OPEN" -> { }
                default -> throw new AssertionError("意外档期状态: " + id + " → " + slot.getStatus());
            }
        }
        assertThat(heldCount).as("A/B/C 中应恰有一个占用档期，其余全部 OPEN（无孤儿 HELD）").isEqualTo(1);
    }

    private Thread rescheduleThread(String token, String bookingId, String targetSlotId,
                                    CyclicBarrier barrier, int[] statusOut, int idx) {
        return new Thread(() -> {
            try {
                barrier.await(TimeUnit.SECONDS.toMillis(10), TimeUnit.MILLISECONDS);
                var result = mvc.perform(post("/api/v1/booking/bookings/%s:reschedule".formatted(bookingId))
                                .header("Authorization", "Bearer " + token)
                                .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(om.writeValueAsBytes(new HashMap<>() {{ put("newSlotId", targetSlotId); }})))
                        .andReturn();
                statusOut[idx] = result.getResponse().getStatus();
            } catch (Exception e) {
                statusOut[idx] = 599;
            }
        });
    }
}
