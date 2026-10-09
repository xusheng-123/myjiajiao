package com.nurture.it;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Release 安全验收（生产形态）：apiDocsPublic=false 时契约与调试 UI 匿名访问被拒（401）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("it")
@Tag("integration")
@TestPropertySource(properties = "nurture.security.apiDocsPublic=false")
class ApiDocsExposureIT {

    @Autowired MockMvc mvc;

    @Test
    @org.junit.jupiter.api.DisplayName("apiDocsPublic=false：契约端点匿名访问被拒(403)")
    void apiDocsRequireAuthWhenClosed() throws Exception {
        mvc.perform(get("/api/v1/api-docs")).andExpect(status().isForbidden());
    }

    @Test
    @org.junit.jupiter.api.DisplayName("apiDocsPublic=false：Swagger UI 匿名访问被拒(403)")
    void swaggerUiRequireAuthWhenClosed() throws Exception {
        mvc.perform(get("/api/v1/swagger-ui/index.html")).andExpect(status().isForbidden());
    }
}
