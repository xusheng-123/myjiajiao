package com.nurture.it;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Release P0-2 安全验收（真 PostgreSQL）：
 * 健康检查匿名可达、只返回最简状态、探针可用、未暴露的 actuator 端点不可达。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("it")
@Tag("integration")
class SecurityExposureIT {

    @Autowired MockMvc mvc;

    @Test
    @org.junit.jupiter.api.DisplayName("健康检查匿名 200 且只返回最简状态")
    void healthEndpointPublicAndMinimal() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                // show-details=never：不得泄露数据库/磁盘等组件细节
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    @org.junit.jupiter.api.DisplayName("liveness/readiness 探针匿名可达")
    void probesAvailable() throws Exception {
        mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
        mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
    }

    @Test
    @org.junit.jupiter.api.DisplayName("未暴露的 actuator 端点（env/beans）不可达(403/404)")
    void sensitiveActuatorEndpointsNotExposed() throws Exception {
        // 未在 permitAll 白名单 → 安全过滤层直接 403；即使未来放开也不会暴露内容（include 仅 health,info → 404）
        mvc.perform(get("/actuator/env"))
                .andExpect(result -> org.assertj.core.api.Assertions.assertThat(
                        result.getResponse().getStatus()).isIn(403, 404));
        mvc.perform(get("/actuator/beans"))
                .andExpect(result -> org.assertj.core.api.Assertions.assertThat(
                        result.getResponse().getStatus()).isIn(403, 404));
    }
}
