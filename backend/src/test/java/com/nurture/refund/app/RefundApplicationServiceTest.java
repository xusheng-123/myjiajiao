package com.nurture.refund.app;

import com.nurture.messaging.app.MessagingApplicationService;
import com.nurture.order.app.OrderApplicationService;
import com.nurture.order.domain.Order;
import com.nurture.parent.app.ParentApplicationService;
import com.nurture.refund.domain.RefundCase;
import com.nurture.refund.infra.RefundCaseRepository;
import com.nurture.shared.audit.AuditService;
import com.nurture.shared.twophase.TwoPhaseService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 退款仲裁预览文案与真实行为一致性（2026-10-10 修正）：
 * 当前执行路径不调整教师课酬结算条目（红冲待批准）——预览不得再出现
 * 「结算条目作废」等未经代码证实的承诺；订单/钱包变化与「未自动冲回」须分开如实表述。
 */
@ExtendWith(MockitoExtension.class)
class RefundApplicationServiceTest {

    @Mock RefundCaseRepository refundRepository;
    @Mock OrderApplicationService orderService;
    @Mock ParentApplicationService parentService;
    @Mock TwoPhaseService twoPhaseService;
    @Mock AuditService auditService;
    @Mock MessagingApplicationService messagingService;
    @Mock com.nurture.settlement.app.SettlementApplicationService settlementService;
    @InjectMocks RefundApplicationService service;

    @Test
    void 预览文案如实描述订单关闭与结算条目不自动调整() {
        UUID orderId = UUID.randomUUID();
        RefundCase refundCase = new RefundCase(orderId, UUID.randomUUID(), "课程质量争议", 30000, 1);
        when(refundRepository.lockById(refundCase.getId())).thenReturn(java.util.Optional.of(refundCase));
        OrderApplicationService.OrderSnapshot order = org.mockito.Mockito.mock(OrderApplicationService.OrderSnapshot.class);
        when(orderService.getSnapshot(orderId)).thenReturn(order);
        when(order.getOrderNo()).thenReturn("NUR-ORD-TEST");
        when(settlementService.refundNote(order)).thenReturn("退款执行时追加教师课酬红冲条目，待结算净额相应扣减 ¥255.00");
        when(twoPhaseService.issue(anyString(), any(), anyString()))
                .thenReturn(new TwoPhaseService.Grant("token-xyz", Instant.now().plusSeconds(600)));

        var preview = service.previewArbitration(refundCase.getId());
        var impact = preview.cascadeImpact();

        assertThat(impact.get("orderAfterRefund"))
                .as("订单关闭是真实会发生的")
                .isEqualTo("已支付订单将关闭");
        assertThat(String.valueOf(impact.get("orderAfterRefund"))).doesNotContain("作废");
        assertThat(String.valueOf(impact.get("settlementNote")))
                .as("结算条目现状必须如实：不自动调整、退款成功≠课酬已红冲")
                .contains("退款执行时追加教师课酬红冲条目")
                .contains("待结算净额相应扣减");
        assertThat(((Number) impact.get("refundAmountCents")).longValue()).isEqualTo(30000L);
        // 预览仅评估（签发凭证 + 置为已评估），不得直接进入已批准/已执行
        assertThat(refundCase.getStatus().name()).isEqualTo("EVALUATED");
    }
}
