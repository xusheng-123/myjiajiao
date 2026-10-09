package com.nurture.payment.app;

import com.nurture.messaging.app.MessagingApplicationService;
import com.nurture.order.app.OrderApplicationService;
import com.nurture.order.domain.Order;
import com.nurture.parent.app.ParentApplicationService;
import com.nurture.payment.domain.PaymentAttempt;
import com.nurture.payment.domain.PaymentGateway;
import com.nurture.payment.infra.PaymentAttemptRepository;
import com.nurture.settlement.app.SettlementApplicationService;
import com.nurture.shared.audit.AuditService;
import com.nurture.shared.security.CallerPrincipal;
import com.nurture.shared.security.CallerRole;
import com.nurture.shared.security.Scope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** D1 配套回归：发起支付的对象级授权（仅付款方/学生本人/运营）。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentApplicationServiceTest {

    @Mock PaymentAttemptRepository attemptRepository;
    @Mock OrderApplicationService orderService;
    @Mock ParentApplicationService parentService;
    @Mock SettlementApplicationService settlementService;
    @Mock MessagingApplicationService messagingService;
    @Mock AuditService auditService;
    @Mock PaymentGateway gateway;

    private PaymentApplicationService service;

    private final UUID payerId = UUID.randomUUID();
    private final UUID outsiderId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();
    private Order order;

    @BeforeEach
    void setUp() {
        service = new PaymentApplicationService(attemptRepository, List.of(gateway),
                orderService, parentService, settlementService, messagingService, auditService);
        order = new Order("NUR-TEST", UUID.randomUUID(), payerId, UUID.randomUUID(),
                UUID.randomUUID(), Order.OrderType.FORMAL_LESSONS, 20000L, "{}");
        when(orderService.get(orderId)).thenReturn(order);
        when(gateway.channelName()).thenReturn("MOCK");
        when(gateway.createPayment(any(), any(Long.class), any())).thenReturn("MOCK-PAY-ref");
        when(attemptRepository.findByOrderIdAndChannel(any(), any()))
                .thenReturn(Optional.empty());
        when(attemptRepository.save(any(PaymentAttempt.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void login(UUID accountId, CallerRole role) {
        var auth = new UsernamePasswordAuthenticationToken(
                new CallerPrincipal(accountId, role, "tester", Set.of(Scope.ORDER_READ)), null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void 付款方可发起支付() {
        login(payerId, CallerRole.PARENT);
        var result = service.initiate(orderId, "MOCK");
        assertThat(result.channelRef()).isEqualTo("MOCK-PAY-ref");
    }

    @Test
    void 无关用户不可对他人订单发起支付() {
        login(outsiderId, CallerRole.STUDENT);
        assertThatThrownBy(() -> service.initiate(orderId, "MOCK"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("付款方");
    }

    @Test
    void 未支付订单被关闭后不可重复发起() {
        order.cancel();
        login(payerId, CallerRole.PARENT);
        assertThatThrownBy(() -> service.initiate(orderId, "MOCK"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("不可发起支付");
    }
}
