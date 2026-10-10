package com.nurture.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nurture.order.app.OrderApplicationService;
import com.nurture.settlement.app.SettlementApplicationService;
import com.nurture.settlement.domain.SettlementEntry;
import com.nurture.settlement.infra.SettlementEntryRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("it") @Tag("integration")
class RefundReversalIT {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired SettlementEntryRepository entries;
    @Autowired SettlementApplicationService settlement;
    @Autowired OrderApplicationService orders;
    @Autowired PlatformTransactionManager txManager;
    Support.Account teacher, student, parent;
    String admin;
    UUID orderId, bookingId;

    @BeforeEach void setup() throws Exception {
        teacher = account("TEACHER"); student = account("STUDENT"); parent = account("PARENT");
        admin = Support.postJson(mvc, om, "/api/v1/identity/auth/login:password", null,
                Map.of("role", "ADMIN", "account", "13800000000", "password", "Admin#2026")).get("accessToken").asText();
        String studentNo = Support.getJson(mvc, om, "/api/v1/student/me/profile", student.accessToken()).get("studentNo").asText();
        Support.postJson(mvc, om, "/api/v1/parent/me/children", parent.accessToken(), Map.of("studentNo", studentNo, "relation", "父亲"));
        Support.postJson(mvc, om, "/api/v1/parent/me/children:confirm", student.accessToken(), Map.of());
        var start = OffsetDateTime.now().plusDays(33);
        var slot = Support.postJson(mvc, om, "/api/v1/scheduling/availability-slots", teacher.accessToken(),
                Map.of("startAt", start.toString(), "endAt", start.plusHours(1).toString()));
        var booking = Support.postJson(mvc, om, "/api/v1/booking/bookings", student.accessToken(),
                Map.of("teacherId", teacher.accountId(), "slotId", slot.get("slotId").asText(), "subject", "物理", "bookingType", "FORMAL"));
        bookingId = UUID.fromString(booking.get("bookingId").asText());
        Support.postJson(mvc, om, "/api/v1/booking/booking-requests/" + bookingId + ":decide?decision=approve", teacher.accessToken(), Map.of());
        var order = Support.postJson(mvc, om, "/api/v1/order/orders", parent.accessToken(), Map.of("bookingId", bookingId));
        orderId = UUID.fromString(order.get("orderId").asText());
        assertThat(write("/api/v1/admin/orders/" + orderId + ":confirm-offline-payment", admin, Map.of())).isEqualTo(200);
    }
    private Support.Account account(String role) throws Exception {
        return Support.register(mvc, om, role.toLowerCase(), role,
                "17" + String.format("%09d", new java.security.SecureRandom().nextInt(1_000_000_000)), "Reversal#2026");
    }
    private int write(String url, String token, Object body) throws Exception {
        return mvc.perform(post(url).header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsBytes(body))).andReturn().getResponse().getStatus();
    }
    private JsonNode request() throws Exception {
        return Support.postJson(mvc, om, "/api/v1/refund/cases", parent.accessToken(), Map.of("orderId", orderId, "reason", "退款测试"));
    }
    private JsonNode preview(JsonNode c) throws Exception {
        return Support.postJson(mvc, om, "/api/v1/admin/refund-cases/" + c.get("caseId").asText() + "/arbitration:preview", admin, Map.of());
    }
    private String executeUrl(JsonNode c) { return "/api/v1/admin/refund-cases/" + c.get("caseId").asText() + "/arbitration:execute"; }
    private long balance() throws Exception {
        return Support.getJson(mvc, om, "/api/v1/parent/me/wallet", parent.accessToken()).get("balanceCents").asLong();
    }
    private SettlementEntry earning() { return entries.findByOrderIdAndEntryKind(orderId, SettlementEntry.EntryKind.EARNING).orElseThrow(); }
    private void assertReversed() {
        var e = earning(); var r = entries.findByReversesEntryId(e.getId()).orElseThrow();
        assertThat(e.getNetCents()).isEqualTo(25500); assertThat(e.getStatus()).isEqualTo(SettlementEntry.SettlementStatus.PENDING);
        assertThat(r.getNetCents()).isEqualTo(-25500); assertThat(r.getGrossCents()).isEqualTo(-30000);
        assertThat(r.getPlatformFeeCents()).isZero(); assertThat(r.getEntryKind()).isEqualTo(SettlementEntry.EntryKind.REVERSAL);
        assertThat(settlement.summaryOf(teacher.accountId()).pendingNetCents()).isZero();
    }
    @Test void cancelPaidReversesExactlyAndCreditsWallet() throws Exception {
        long before = balance();
        var p = Support.postJson(mvc, om, "/api/v1/booking/bookings/" + bookingId + ":cancel/preview", student.accessToken(), Map.of("reason", "取消"));
        assertThat(write("/api/v1/booking/bookings/" + bookingId + ":cancel/execute", student.accessToken(),
                Map.of("reason", "取消", "confirmationToken", p.get("confirmationToken").asText()))).isEqualTo(200);
        assertReversed(); assertThat(balance()).isEqualTo(before + 30000);
    }
    @Test void arbitrationReversesAndClosesOrder() throws Exception {
        var c = request(); var p = preview(c); long before = balance();
        assertThat(write(executeUrl(c), admin, Map.of("approve", true, "decisionNote", "同意", "confirmationToken", p.get("confirmationToken").asText()))).isEqualTo(200);
        assertReversed(); assertThat(balance()).isEqualTo(before + 30000);
        assertThat(orders.get(orderId).getStatus().name()).isEqualTo("CLOSED");
        assertThat(write(executeUrl(c), admin, Map.of("approve", false, "decisionNote", "重复驳回"))).isEqualTo(409);
    }
    @Test void previewAndRejectionDoNotReverse() throws Exception {
        var c = request(); var p = preview(c);
        assertThat(p.get("cascadeImpact").get("settlementNote").asText()).contains("红冲");
        assertThat(write(executeUrl(c), admin, Map.of("approve", false, "decisionNote", "驳回"))).isEqualTo(200);
        assertThat(entries.findByReversesEntryId(earning().getId())).isEmpty();
        assertThat(settlement.summaryOf(teacher.accountId()).pendingNetCents()).isEqualTo(25500);
    }
    @Test void unrelatedRequesterForbidden() throws Exception {
        assertThat(write("/api/v1/refund/cases", teacher.accessToken(), Map.of("orderId", orderId, "reason", "越权"))).isEqualTo(403);
        var outsider = account("PARENT");
        assertThat(write("/api/v1/refund/cases", outsider.accessToken(), Map.of("orderId", orderId, "reason", "越权"))).isEqualTo(403);
    }
    @Test void reversalIsIdempotent() {
        new TransactionTemplate(txManager).executeWithoutResult(s -> { settlement.reverseForRefund(orders.get(orderId)); settlement.reverseForRefund(orders.get(orderId)); });
        assertReversed(); assertThat(settlement.summaryOf(teacher.accountId()).entries()).hasSize(2);
    }
    @Test void rollbackRemovesReversalAndKeepsOriginal() {
        assertThatThrownBy(() -> new TransactionTemplate(txManager).executeWithoutResult(s -> {
            settlement.reverseForRefund(orders.get(orderId)); throw new IllegalStateException("模拟后续入账失败");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(entries.findByReversesEntryId(earning().getId())).isEmpty();
        assertThat(settlement.summaryOf(teacher.accountId()).pendingNetCents()).isEqualTo(25500);
    }
    @Test void paidOutNeedsManualHandlingAndRefundRollsBack() throws Exception {
        new TransactionTemplate(txManager).executeWithoutResult(s -> { var e = earning(); e.markSettled(); e.markPaidOut(); entries.save(e); });
        var c = request(); var p = preview(c); long before = balance();
        assertThat(write(executeUrl(c), admin, Map.of("approve", true, "decisionNote", "同意", "confirmationToken", p.get("confirmationToken").asText()))).isEqualTo(409);
        assertThat(balance()).isEqualTo(before); assertThat(orders.get(orderId).getStatus().name()).isEqualTo("PAID");
        assertThat(entries.findByReversesEntryId(earning().getId())).isEmpty();
    }
    @Test void concurrentArbitrationOnlyOneWalletCredit() throws Exception {
        var c = request(); var p = preview(c); long before = balance();
        try (var pool = Executors.newFixedThreadPool(4)) {
            var gate = new CountDownLatch(1); List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < 4; i++) results.add(pool.submit(() -> { gate.await(); return write(executeUrl(c), admin,
                    Map.of("approve", true, "decisionNote", "并发", "confirmationToken", p.get("confirmationToken").asText())); }));
            gate.countDown(); List<Integer> codes = new ArrayList<>();
            for (var result : results) codes.add(result.get(20, TimeUnit.SECONDS));
            assertThat(codes).containsOnly(200, 409); assertThat(Collections.frequency(codes, 200)).isEqualTo(1);
        }
        assertReversed(); assertThat(balance()).isEqualTo(before + 30000);
    }

    @Test void simultaneousRefundRequestsCreateOnlyOneCase() throws Exception {
        try (var pool = Executors.newFixedThreadPool(4)) {
            var gate = new CountDownLatch(1); List<Future<Integer>> results = new ArrayList<>();
            for (int i=0;i<4;i++) results.add(pool.submit(() -> { gate.await(); return write("/api/v1/refund/cases", parent.accessToken(), Map.of("orderId", orderId, "reason", "并发申请")); }));
            gate.countDown(); List<Integer> statuses = new ArrayList<>();
            for (var result : results) statuses.add(result.get(20, TimeUnit.SECONDS));
            assertThat(statuses).containsOnly(201, 409); assertThat(statuses.stream().filter(s -> s == 201).count()).isEqualTo(1);
        }
    }
    @Test void cancellationCannotBypassExistingArbitration() throws Exception {
        var c = request(); long before = balance();
        var p = Support.postJson(mvc, om, "/api/v1/booking/bookings/"+bookingId+":cancel/preview", student.accessToken(), Map.of("reason", "取消"));
        assertThat(write("/api/v1/booking/bookings/"+bookingId+":cancel/execute", student.accessToken(),
                Map.of("reason", "取消", "confirmationToken", p.get("confirmationToken").asText()))).isEqualTo(409);
        assertThat(orders.get(orderId).getStatus().name()).isEqualTo("PAID"); assertThat(balance()).isEqualTo(before);
        assertThat(entries.findByReversesEntryId(earning().getId())).isEmpty();
        var evaluated = preview(c);
        assertThat(write(executeUrl(c), admin, Map.of("approve", true, "decisionNote", "同意", "confirmationToken", evaluated.get("confirmationToken").asText()))).isEqualTo(200);
        assertReversed(); assertThat(balance()).isEqualTo(before+30000);
    }
    @Test void evaluatedCaseCanRefreshPreviewBeforeExecute() throws Exception {
        var c = request(); var first = preview(c); var refreshed = preview(c);
        assertThat(refreshed.get("confirmationToken").asText()).isNotEqualTo(first.get("confirmationToken").asText());
        assertThat(write(executeUrl(c), admin, Map.of("approve", true, "decisionNote", "同意", "confirmationToken", refreshed.get("confirmationToken").asText()))).isEqualTo(200);
        assertReversed();
    }
    @Test void earningUniqueConstraintSurvivesMigration() {
        var original = earning();
        assertThatThrownBy(() -> entries.saveAndFlush(new SettlementEntry(teacher.accountId(), bookingId, orderId, 30000, 4500)))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(earning().getId()).isEqualTo(original.getId());
    }
}
