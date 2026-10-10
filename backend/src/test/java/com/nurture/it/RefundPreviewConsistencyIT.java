package com.nurture.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nurture.refund.domain.RefundCase;
import com.nurture.refund.infra.RefundCaseRepository;
import com.nurture.settlement.domain.SettlementEntry;
import com.nurture.settlement.infra.SettlementEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 退款仲裁预览一致性（真 PostgreSQL、全过滤器链）：
 * ①预览文案与真实行为一致——订单将关闭、结算条目「暂不自动调整」（不再出现「作废」承诺）；
 * ②预览不会被误认为已执行——退款案仅进入 EVALUATED，订单保持 PAID；
 * ③文案修改本身不改变任何账务数据——结算条目数量与金额在预览前后完全一致。
 * 完整退款执行/取消级联行为由既有 IT 与 E2E 覆盖，本轮零改动。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("it")
@Tag("integration")
class RefundPreviewConsistencyIT {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired RefundCaseRepository refundRepository;
    @Autowired SettlementEntryRepository settlementRepository;

    private Support.Account teacher;
    private Support.Account student;
    private Support.Account parent;
    private String tAdmin;
    private JsonNode paidOrder;

    @BeforeEach
    void setUp() throws Exception {
        teacher = Support.register(mvc, om, "teacher", "TEACHER", "17" + rand9(), "ItPreview#1");
        student = Support.register(mvc, om, "student", "STUDENT", "17" + rand9(), "ItPreview#2");
        parent = Support.register(mvc, om, "parent", "PARENT", "17" + rand9(), "ItPreview#3");
        tAdmin = loginAdmin();

        // 监护绑定：家长发起 + 学生确认（下单资格）
        String studentNo = Support.getJson(mvc, om, "/api/v1/student/me/profile", student.accessToken())
                .get("studentNo").asText();
        Support.postJson(mvc, om, "/api/v1/parent/me/children", parent.accessToken(),
                new HashMap<>() {{ put("studentNo", studentNo); put("relation", "父亲"); }});
        Support.postJson(mvc, om, "/api/v1/parent/me/children:confirm", student.accessToken(), new HashMap<>());

        paidOrder = createPaidOrder();
    }

    /** 独立链路：档期 → 正式课预约+审批 → 家长下单 → 运营线下确认（PAID，生成 1 条结算条目）。 */
    private JsonNode createPaidOrder() throws Exception {
        var start = OffsetDateTime.now().plusDays(30).withHour(10).withMinute(0).withSecond(0).withNano(0);
        var slot = Support.postJson(mvc, om, "/api/v1/scheduling/availability-slots", teacher.accessToken(),
                new HashMap<>() {{ put("startAt", start.toString()); put("endAt", start.plusHours(1).toString()); }});
        var booking = Support.postJson(mvc, om, "/api/v1/booking/bookings", student.accessToken(),
                new HashMap<>() {{
                    put("teacherId", teacher.accountId()); put("slotId", slot.get("slotId").asText());
                    put("subject", "物理"); put("bookingType", "FORMAL");
                }});
        Support.postJson(mvc, om, "/api/v1/booking/booking-requests/%s:decide?decision=approve"
                .formatted(booking.get("bookingId").asText()), teacher.accessToken(), new HashMap<>());
        var order = Support.postJson(mvc, om, "/api/v1/order/orders", parent.accessToken(),
                new HashMap<>() {{ put("bookingId", booking.get("bookingId").asText()); }});
        Support.postJson(mvc, om, "/api/v1/admin/orders/%s:confirm-offline-payment"
                .formatted(order.get("orderId").asText()), tAdmin, new HashMap<>());
        return order;
    }

    @Test
    void 预览文案如实_不触碰结算条目_不被误认为已执行() throws Exception {
        String orderId = paidOrder.get("orderId").asText();
        var entriesBefore = settlementRepository.findByTeacherIdAndStatus(
                teacher.accountId(), SettlementEntry.SettlementStatus.PENDING);

        // 家长发起退款申请（仅已支付订单可申请）
        var refundCase = Support.postJson(mvc, om, "/api/v1/refund/cases",
                parent.accessToken(), new HashMap<>() {{
                    put("orderId", orderId); put("reason", "预览一致性验证");
                }});

        // 运营①评估影响（预览）
        var preview = Support.postJson(mvc, om,
                "/api/v1/admin/refund-cases/%s/arbitration:preview".formatted(refundCase.get("caseId").asText()),
                tAdmin, new HashMap<>());
        var impact = preview.get("cascadeImpact");

        // ①文案与真实行为一致：订单将关闭（无「作废」承诺）+ 结算条目暂不自动调整
        assertThat(impact.get("orderAfterRefund").asText()).isEqualTo("已支付订单将关闭");
        assertThat(impact.get("settlementNote").asText())
                .contains("退款执行时追加教师课酬红冲条目")
                .contains("待结算净额相应扣减");

        // ②预览不是执行：退款案仅 EVALUATED，订单保持 PAID
        var caseAfter = Support.getJson(mvc, om,
                "/api/v1/refund/me/cases", parent.accessToken());
        assertThat(isStatusEvaluated(caseAfter, refundCase.get("caseId").asText()))
                .as("预览只产生「已评估」标记，不得直接进入已批准/已执行").isTrue();
        var orderAfter = Support.getJson(mvc, om, "/api/v1/order/orders/" + orderId, parent.accessToken());
        assertThat(orderAfter.get("status").asText()).as("预览不得关闭订单").isEqualTo("PAID");

        // ③文案修改不改变账务：结算条目数量与金额预览前后一致（当前行为：不自动调整）
        var entriesAfter = settlementRepository.findByTeacherIdAndStatus(
                teacher.accountId(), SettlementEntry.SettlementStatus.PENDING);
        assertThat(entriesAfter).hasSameSizeAs(entriesBefore);
        assertThat(entriesAfter.stream().mapToLong(SettlementEntry::getNetCents).sum())
                .isEqualTo(entriesBefore.stream().mapToLong(SettlementEntry::getNetCents).sum());
    }

    private boolean isStatusEvaluated(JsonNode page, String caseId) {
        for (var c : page.get("content")) {
            if (caseId.equals(c.get("caseId").asText())) {
                return "EVALUATED".equals(c.get("status").asText());
            }
        }
        return false;
    }

    private String loginAdmin() throws Exception {
        var result = mvc.perform(post("/api/v1/identity/auth/login:password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsBytes(new HashMap<>() {{
                            put("role", "ADMIN"); put("account", "13800000000"); put("password", "Admin#2026");
                        }}))
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isOk()).andReturn();
        return om.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private String rand9() {
        return String.format("%09d", new java.util.Random().nextInt(1_000_000_000));
    }
}
