package com.nurture.it;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nurture.verification.app.VerificationApplicationService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("it") @Tag("integration")
class PublicTeacherDiscoveryIT {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired VerificationApplicationService verification;
    Support.Account teacher;
    String subject, admin, offeringId;
    @BeforeEach void setup() throws Exception {
        teacher = Support.register(mvc, om, "teacher", "TEACHER", "15"+String.format("%09d",new java.security.SecureRandom().nextInt(1_000_000_000)), "Discovery#2026");
        var initial = Support.getJson(mvc, om, "/api/v1/teacher/me/profile", teacher.accessToken());
        assertThat(initial.get("school").asText()).isEqualTo("集成测试大学");
        assertThat(initial.get("highestDegree").asText()).isEqualTo("硕士");
        assertThat(initial.get("yearsOfTeaching").asInt()).isEqualTo(2);
        subject = "检索" + UUID.randomUUID().toString().substring(0, 8);
        admin = Support.postJson(mvc, om, "/api/v1/identity/auth/login:password", null, Map.of("role", "ADMIN", "account", "13800000000", "password", "Admin#2026")).get("accessToken").asText();
        mvc.perform(put("/api/v1/teacher/me/profile").header("Authorization", "Bearer "+teacher.accessToken()).header("Idempotency-Key",UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(Map.of("school","真实测试大学","highestDegree","博士","yearsOfTeaching",7,"intro","因材施教","styleTags",List.of("耐心细致")))))
                .andExpect(status().isOk());
        var offering = Support.postJson(mvc, om, "/api/v1/teacher/me/subjects", teacher.accessToken(), Map.of("subject",subject,"gradeBand","高中","pricePerHourCents",18000));
        offeringId = offering.get("offeringId").asText();
    }
    void approve() throws Exception {
        byte[] pdf="%PDF-1.4\nProof\n%%EOF".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        mvc.perform(multipart("/api/v1/verification/cases").file(new MockMultipartFile("idDocument","id.pdf","application/pdf",pdf))
                .file(new MockMultipartFile("degreeCert","degree.pdf","application/pdf",pdf)).header("Authorization","Bearer "+teacher.accessToken()).header("Idempotency-Key",UUID.randomUUID().toString()))
                .andExpect(status().isCreated());
        var c=verification.latestOfTeacher(teacher.accountId());
        Support.postJson(mvc,om,"/api/v1/admin/verification-cases/"+c.getId()+":decide",admin,Map.of("approve",true,"note","核验通过"));
    }
    @Test void publicSearchHasRealProfileAndFilters() throws Exception {
        approve();
        var result=mvc.perform(get("/api/v1/matching/public/teachers").param("subject",subject).param("styleTag","耐心细致"))
                .andExpect(status().isOk()).andReturn();
        var rows=om.readTree(result.getResponse().getContentAsString());
        var row=java.util.stream.StreamSupport.stream(rows.spliterator(),false)
                .filter(t->t.get("teacherId").asText().equals(teacher.accountId().toString())).findFirst().orElseThrow();
        assertThat(row.get("displayName").asText()).isEqualTo("IT-TEACHER");
        assertThat(row.get("school").asText()).isEqualTo("真实测试大学");
        assertThat(row.get("yearsOfTeaching").asInt()).isEqualTo(7);
        assertThat(row.get("styleTags").get(0).asText()).isEqualTo("耐心细致");
        assertThat(row.has("phone")).isFalse(); assertThat(row.has("idDocumentKey")).isFalse();
        var inexpensive=Support.getJson(mvc,om,"/api/v1/matching/public/teachers?subject="+java.net.URLEncoder.encode(subject, java.nio.charset.StandardCharsets.UTF_8)+"&maxPricePerHourCents=10000",null);
        assertThat(inexpensive.findValuesAsText("teacherId")).doesNotContain(teacher.accountId().toString());
        inexpensive.forEach(t->assertThat(t.get("pricePerHourCents").asLong()).isLessThanOrEqualTo(10000));
        var d=Support.getJson(mvc,om,"/api/v1/matching/public/teachers/"+teacher.accountId(),null);
        assertThat(d.get("intro").asText()).isEqualTo("因材施教"); assertThat(d.get("offerings").size()).isEqualTo(1);
    }
    @Test void unverifiedProfileRemainsPrivateEvenWithFlag() throws Exception {
        var response=mvc.perform(get("/api/v1/matching/public/teachers").param("subject",subject).param("includeUnverified","true"))
                .andExpect(status().isOk()).andReturn();
        var rows=om.readTree(response.getResponse().getContentAsString());
        assertThat(rows.findValuesAsText("teacherId")).doesNotContain(teacher.accountId().toString());
        rows.forEach(t->assertThat(t.get("verificationApproved").asBoolean()).isTrue());
        mvc.perform(get("/api/v1/matching/public/teachers/"+teacher.accountId())).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/matching/teachers:search")).andExpect(status().isForbidden());
    }
    @Test void delistedAndLockedTeachersDisappear() throws Exception {
        approve();
        mvc.perform(delete("/api/v1/teacher/me/subjects/"+offeringId).header("Authorization","Bearer "+teacher.accessToken()).header("Idempotency-Key",UUID.randomUUID().toString()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/matching/public/teachers/"+teacher.accountId())).andExpect(status().isNotFound());
        Support.postJson(mvc,om,"/api/v1/teacher/me/subjects",teacher.accessToken(),Map.of("subject",subject,"gradeBand","初中","pricePerHourCents",18000));
        Support.postJson(mvc,om,"/api/v1/admin/accounts/"+teacher.accountId()+":lock",admin,Map.of("note","冻结测试"));
        var rows=om.readTree(mvc.perform(get("/api/v1/matching/public/teachers").param("subject",subject))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(rows.findValuesAsText("teacherId")).doesNotContain(teacher.accountId().toString());
        mvc.perform(get("/api/v1/matching/public/teachers/"+teacher.accountId())).andExpect(status().isNotFound());
    }
    @Test void selectedTeacherIsFilteredBeforeResultLimit() throws Exception {
        approve();
        mvc.perform(get("/api/v1/matching/teachers:search").param("teacherId",teacher.accountId().toString()).header("Authorization","Bearer "+admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].teacherId").value(teacher.accountId().toString()))
                .andExpect(jsonPath("$.length()").value(1));
    }
    @Test void smsIsHonestlyUnavailableWithoutSupplier() throws Exception {
        mvc.perform(get("/api/v1/identity/auth/capabilities")).andExpect(status().isOk()).andExpect(jsonPath("$.smsAvailable").value(false));
        mvc.perform(post("/api/v1/identity/auth/sms-codes").header("Idempotency-Key",UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"STUDENT\",\"phone\":\"13912345678\"}"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.detail").value("短信服务尚未开通，请使用账号密码登录"));
    }
}
