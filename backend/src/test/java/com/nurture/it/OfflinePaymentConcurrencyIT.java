package com.nurture.it;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nurture.order.app.OrderApplicationService;
import com.nurture.payment.app.PaymentApplicationService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 支付模式 B 并发确认实测（真 PostgreSQL）：N 个运营操作者同时对同一 PENDING_PAYMENT 订单
 * 调用 confirmOfflinePayment —— 必须恰好一次成功状态迁移，副作用（支付/结算/通知/审计）各恰好一次。
 * 直接经 Spring 事务代理调用应用服务（每线程独立事务），真实触发数据库层的并发竞争。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("it")
@Tag("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OfflinePaymentConcurrencyIT {

    private static final int CONCURRENT_OPERATORS = 6;

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired JdbcTemplate jdbc;
    @Autowired PaymentApplicationService paymentService;
    @Autowired OrderApplicationService orderService;

    private String tTeacher;
    private String tStudent;
    private String tParent;
    private String teacherId;
    private UUID targetOrderId;
    private UUID targetBookingId;
    /** 测试启动前的 offline-confirm 审计基线（断言本轮恰好 +1，与其他 IT 的数据隔离） */
    private int auditBaseline;

    /** 生成合法测试手机号：1[3-9] + 9 位（前缀 3 位 + suffix 7 位 + 序号 1 位 → 共 11 位）。 */
    private static String phone(String uuidSuffix, int seq) {
        String digits = uuidSuffix.replaceAll("[^0-9]", "x");
        return "137" + digits + seq; // 3 位前缀 + suffix 纯数字 7 位 + 序号 = 11 位
    }

    @BeforeAll
    void setUp() throws Exception {
        String suffix = String.format("%07d", new java.security.SecureRandom().nextInt(10_000_000));
        var teacher = Support.register(mvc, om, "teacher", "TEACHER", phone(suffix, 1), "ConcIT#2026");
        var student = Support.register(mvc, om, "student", "STUDENT", phone(suffix, 2), "ConcIT#2026");
        var parent = Support.register(mvc, om, "parent", "PARENT", phone(suffix, 3), "ConcIT#2026");
        tTeacher = teacher.accessToken();
        tStudent = student.accessToken();
        tParent = parent.accessToken();
        teacherId = teacher.accountId().toString();
        String studentNo = Support.getJson(mvc, om, "/api/v1/student/me/profile", tStudent)
                .get("studentNo").asText();
        Support.postJson(mvc, om, "/api/v1/parent/me/children", tParent,
                new java.util.HashMap<>() {{ put("studentNo", studentNo); put("relation", "父亲"); }});
        Support.postJson(mvc, om, "/api/v1/parent/me/children:confirm", tStudent, new java.util.HashMap<>());

        var start = OffsetDateTime.now().plusDays(25).withHour(10).withMinute(0).withSecond(0).withNano(0);
        var slot = Support.postJson(mvc, om, "/api/v1/scheduling/availability-slots", tTeacher,
                new java.util.HashMap<>() {{ put("startAt", start.toString()); put("endAt", start.plusHours(1).toString()); }});
        var booking = Support.postJson(mvc, om, "/api/v1/booking/bookings", tStudent,
                new java.util.HashMap<>() {{
                    put("teacherId", teacherId); put("slotId", slot.get("slotId").asText());
                    put("subject", "并发确认"); put("bookingType", "FORMAL");
                }});
        // 试听免费规则（2026-10-10）：付费订单须走正式课审批流
        Support.postJson(mvc, om,
                "/api/v1/booking/booking-requests/%s:decide?decision=approve".formatted(booking.get("bookingId").asText()),
                tTeacher, new java.util.HashMap<>());
        targetBookingId = UUID.fromString(booking.get("bookingId").asText());
        var order = Support.postJson(mvc, om, "/api/v1/order/orders", tParent,
                new java.util.HashMap<>() {{ put("bookingId", targetBookingId); }});
        targetOrderId = UUID.fromString(order.get("orderId").asText());
        auditBaseline = jdbc.queryForObject(
                "SELECT count(*) FROM audit_logs WHERE action_type='payment.offline-confirm' AND execution_status='SUCCESS'",
                Integer.class);
    }

    @Test
    @DisplayName("6 运营并发确认同一订单：恰好 1 次成功，支付/结算/通知/审计各恰好一次")
    void concurrentConfirmProducesExactlyOneTransition() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_OPERATORS);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(CONCURRENT_OPERATORS);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();

        List<Future<String>> results = new java.util.ArrayList<>();
        for (int i = 0; i < CONCURRENT_OPERATORS; i++) {
            final int op = i;
            results.add(pool.submit(() -> {
                startGate.await();
                try {
                    // 不同操作者 = 不同幂等键（幂等过滤器不拦，考验业务状态机 + 数据库约束）
                    var outcome = paymentService.confirmOfflinePayment(targetOrderId);
                    successes.incrementAndGet();
                    done.countDown();
                    return "OK:" + outcome.get("status");
                } catch (Exception e) {
                    failures.incrementAndGet();
                    done.countDown();
                    return "FAIL:" + e.getClass().getSimpleName() + ":" + e.getMessage();
                }
            }));
        }
        startGate.countDown();
        done.await(60, TimeUnit.SECONDS);
        pool.shutdownNow();

        List<String> outcomes = results.stream().map(f -> {
            try {
                return f.get();
            } catch (Exception e) {
                return "ERR:" + e.getMessage();
            }
        }).toList();
        System.out.println("[ConcurrencyIT] 各线程结果: " + outcomes);

        // 核心断言 1：恰好一次成功状态迁移
        assertThat(successes.get()).as("成功确认数必须为 1，实际结果=%s", outcomes).isEqualTo(1);
        assertThat(failures.get()).as("其余 %d 个并发请求必须失败", CONCURRENT_OPERATORS - 1)
                .isEqualTo(CONCURRENT_OPERATORS - 1);

        // 核心断言 2：数据库终态
        String status = jdbc.queryForObject("SELECT status FROM orders WHERE id=?",
                String.class, targetOrderId);
        assertThat(status).as("订单终态必须 PAID").isEqualTo("PAID");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM payment_attempts WHERE order_id=? AND status='SUCCEEDED' AND channel='OFFLINE'",
                Integer.class, targetOrderId)).as("成功支付记录恰好 1 条").isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM settlement_entries WHERE booking_id=?",
                Integer.class, targetBookingId)).as("结算恰好 1 条（ux_settlement_booking 兜底）").isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM system_notices sn JOIN orders o ON "
                        + "(sn.recipient_id=o.teacher_id OR sn.recipient_id=o.payer_parent_id) "
                        + "WHERE o.booking_id=? AND sn.notice_type IN ('SETTLEMENT','PAYMENT') "
                        + "AND sn.body LIKE '%'||o.order_no||'%'",
                Integer.class, targetBookingId)).as("成功通知恰好 2 条").isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM audit_logs WHERE action_type='payment.offline-confirm' AND execution_status='SUCCESS'",
                Integer.class)).as("离线确认审计必须恰好比基线 +1").isEqualTo(auditBaseline + 1);

        // 核心断言 3：失败方回滚后不存在半成功状态
        Integer pendingAttempts = jdbc.queryForObject(
                "SELECT count(*) FROM payment_attempts WHERE order_id=? AND status='INITIATED'",
                Integer.class, targetOrderId);
        assertThat(pendingAttempts).as("不得残留 INITIATED 半成功尝试").isZero();
    }
}
