package com.nurture.it;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nurture.messaging.domain.Message;
import com.nurture.messaging.infra.MessageRepository;
import com.nurture.shared.audit.AuditLogRepository;
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
class ConsultationAndMaterialIT {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired MessageRepository messages;
    @Autowired AuditLogRepository audit;
    @Autowired com.nurture.verification.app.VerificationApplicationService verification;
    Support.Account teacher, student, outsider;
    UUID conversation;
    @BeforeEach void setup() throws Exception {
        teacher = account("TEACHER"); student = account("STUDENT"); outsider = account("STUDENT");
        var c = Support.postJson(mvc, om, "/api/v1/messaging/conversations", student.accessToken(), Map.of("teacherId", teacher.accountId(), "subject", "数学"));
        conversation = UUID.fromString(c.get("conversationId").asText());
    }
    Support.Account account(String role) throws Exception {
        return Support.register(mvc, om, role.toLowerCase(), role, "16" + String.format("%09d", new java.security.SecureRandom().nextInt(1_000_000_000)), "Consult#2026");
    }
    int postStatus(String path, String token, Object body) throws Exception {
        return mvc.perform(post(path).header("Authorization", "Bearer " + token).header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(body))).andReturn().getResponse().getStatus();
    }
    String path() { return "/api/v1/messaging/conversations/" + conversation; }
    @Test void messagesUnreadReadReceiptAndIdempotency() throws Exception {
        String key = UUID.randomUUID().toString();
        for (int i=0; i<2; i++) mvc.perform(post(path()+"/messages").header("Authorization", "Bearer "+student.accessToken())
                .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"学习目标\"}"))
                .andExpect(status().isCreated());
        assertThat(messages.findByConversationIdOrderBySentAtDescIdDesc(conversation, org.springframework.data.domain.PageRequest.of(0, 100))).hasSize(1);
        var list = Support.getJson(mvc, om, "/api/v1/messaging/conversations", teacher.accessToken());
        assertThat(list.get(0).get("unreadCount").asInt()).isEqualTo(1);
        assertThat(list.get(0).get("counterpartName").asText()).isEqualTo("IT-STUDENT");
        assertThat(postStatus(path()+":read", teacher.accessToken(), Map.of())).isEqualTo(200);
        var rows = Support.getJson(mvc, om, path()+"/messages", student.accessToken());
        assertThat(rows.get(0).get("mine").asBoolean()).isTrue(); assertThat(rows.get(0).get("readAt").isNull()).isFalse();
        assertThat(postStatus(path()+":read", teacher.accessToken(), Map.of())).isEqualTo(200);
        assertThat(Support.getJson(mvc, om, "/api/v1/messaging/conversations", teacher.accessToken()).get(0).get("unreadCount").asInt()).isZero();
    }
    @Test void participantAuthorizationAndInputValidation() throws Exception {
        mvc.perform(get(path()+"/messages").header("Authorization", "Bearer "+outsider.accessToken())).andExpect(status().isForbidden());
        assertThat(postStatus(path()+"/messages", outsider.accessToken(), Map.of("body", "越权"))).isEqualTo(403);
        assertThat(postStatus(path()+":read", outsider.accessToken(), Map.of())).isEqualTo(403);
        assertThat(postStatus(path()+"/messages", student.accessToken(), Map.of("body", " "))).isEqualTo(400);
        assertThat(postStatus(path()+"/messages", student.accessToken(), Map.of("body", "x".repeat(4001)))).isEqualTo(400);
        assertThat(postStatus("/api/v1/messaging/conversations", student.accessToken(), Map.of("teacherId", outsider.accountId()))).isEqualTo(400);
        assertThat(postStatus("/api/v1/messaging/conversations", student.accessToken(), Map.of("subject", "数学"))).isEqualTo(400);
    }
    @Test void newestMessagesAndOlderPageAreBothReachable() throws Exception {
        for (int i=0;i<105;i++) messages.saveAndFlush(new Message(conversation, student.accountId(), Message.SenderRole.STUDENT, "序号"+i));
        var latest = Support.getJson(mvc, om, path()+"/messages", teacher.accessToken());
        var older = Support.getJson(mvc, om, path()+"/messages?page=1", teacher.accessToken());
        assertThat(latest.size()).isEqualTo(100); assertThat(latest.get(99).get("body").asText()).isEqualTo("序号104");
        assertThat(older.size()).isEqualTo(5); assertThat(older.get(0).get("body").asText()).isEqualTo("序号0");
    }
    @Test void reopeningReturnsSameConversation() throws Exception {
        var again = Support.postJson(mvc, om, "/api/v1/messaging/conversations", student.accessToken(), Map.of("teacherId", teacher.accountId(), "subject", "语文"));
        assertThat(again.get("conversationId").asText()).isEqualTo(conversation.toString());
    }
    @Test void privateMaterialRequiresAdminAndIsAuditedWithoutCaching() throws Exception {
        byte[] pdf = "%PDF-1.4\nIT proof\n%%EOF".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var upload = mvc.perform(multipart("/api/v1/verification/cases")
                .file(new MockMultipartFile("idDocument", "id.pdf", "application/pdf", pdf))
                .file(new MockMultipartFile("degreeCert", "degree.pdf", "application/pdf", pdf))
                .header("Authorization", "Bearer "+teacher.accessToken()).header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isCreated()).andReturn();
        String caseId = verification.latestOfTeacher(teacher.accountId()).getId().toString();
        String url = "/api/v1/admin/verification-cases/"+caseId+"/materials/identity";
        mvc.perform(get(url)).andExpect(status().isForbidden());
        mvc.perform(get(url).header("Authorization", "Bearer "+teacher.accessToken())).andExpect(status().isForbidden());
        mvc.perform(get(url).header("Authorization", "Bearer "+student.accessToken())).andExpect(status().isForbidden());
        String admin = Support.postJson(mvc, om, "/api/v1/identity/auth/login:password", null,
                Map.of("role", "ADMIN", "account", "13800000000", "password", "Admin#2026")).get("accessToken").asText();
        long before = audit.count();
        mvc.perform(get(url).header("Authorization", "Bearer "+admin)).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store, private"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment;")))
                .andExpect(content().bytes(pdf));
        assertThat(audit.count()).isGreaterThan(before);
        mvc.perform(get(url.replace("identity", "arbitrary")).header("Authorization", "Bearer "+admin)).andExpect(status().isBadRequest());
    }
}
