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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** D9 集成回归（真 PostgreSQL）：同导师+科目+学段重复创建幂等返回既有条目（部分唯一索引兜底）。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("it")
@Tag("integration")
class SubjectIdempotencyIT {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    private final Random random = new Random();

    private String phone() {
        return "17" + String.format("%09d", random.nextInt(1_000_000_000));
    }

    @Test
    void 重复开课条目幂等返回同一条目() throws Exception {
        var teacher = Support.register(mvc, om, "teacher", "TEACHER", phone(), "ItTeacher#1");

        var body = new java.util.HashMap<String, Object>() {{
            put("subject", "英语");
            put("gradeBand", "初中");
            put("pricePerHourCents", 15000);
        }};
        var first = mvc.perform(post("/api/v1/teacher/me/subjects")
                        .header("Authorization", "Bearer " + teacher.accessToken())
                        .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsBytes(body)))
                .andReturn();
        assertThat(first.getResponse().getStatus()).isIn(200, 201);
        String firstId = om.readTree(first.getResponse().getContentAsString()).get("offeringId").asText();

        var second = mvc.perform(post("/api/v1/teacher/me/subjects")
                        .header("Authorization", "Bearer " + teacher.accessToken())
                        .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsBytes(body)))
                .andReturn();
        assertThat(second.getResponse().getStatus()).isIn(200, 201);
        String secondId = om.readTree(second.getResponse().getContentAsString()).get("offeringId").asText();

        assertThat(secondId).isEqualTo(firstId);
    }

    @Test
    void 新增课程价格必须为正数_与调价路径同一约束() throws Exception {
        var teacher = Support.register(mvc, om, "teacher", "TEACHER", phone(), "ItTeacher#1");

        // 0 元与负数定价均被拒（此前仅调价路径有校验，新增路径缺失）
        for (long badPrice : new long[]{0L, -100L}) {
            var bad = mvc.perform(post("/api/v1/teacher/me/subjects")
                            .header("Authorization", "Bearer " + teacher.accessToken())
                            .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsBytes(new java.util.HashMap<>() {{
                                put("subject", "化学");
                                put("gradeBand", "高中");
                                put("pricePerHourCents", badPrice);
                            }})))
                    .andReturn();
            assertThat(bad.getResponse().getStatus())
                    .as("价格 %d 应被拒绝", badPrice).isGreaterThanOrEqualTo(400);
        }

        // 合法定价仍可正常创建
        var ok = mvc.perform(post("/api/v1/teacher/me/subjects")
                        .header("Authorization", "Bearer " + teacher.accessToken())
                        .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsBytes(new java.util.HashMap<>() {{
                            put("subject", "化学");
                            put("gradeBand", "高中");
                            put("pricePerHourCents", 20000);
                        }})))
                .andReturn();
        assertThat(ok.getResponse().getStatus()).isIn(200, 201);
        assertThat(om.readTree(ok.getResponse().getContentAsString()).get("pricePerHourCents").asLong())
                .isEqualTo(20000L);
    }
}
