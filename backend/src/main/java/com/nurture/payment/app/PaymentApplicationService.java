package com.nurture.payment.app;

import com.nurture.messaging.app.MessagingApplicationService;
import com.nurture.order.app.OrderApplicationService;
import com.nurture.order.app.OrderApplicationService.OrderSnapshot;
import com.nurture.parent.app.ParentApplicationService;
import com.nurture.payment.domain.PaymentAttempt;
import com.nurture.payment.domain.PaymentGateway;
import com.nurture.payment.infra.PaymentAttemptRepository;
import com.nurture.payment.infra.SimulatedPaymentGateway;
import com.nurture.settlement.app.SettlementApplicationService;
import com.nurture.shared.audit.AuditService;
import com.nurture.shared.web.UntrustedCallbackException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 支付网关应用服务 — 渠道适配、凭据校验、回调防重入与支付成功联动。
 */
@Service
public class PaymentApplicationService {

    private static final Logger log = LoggerFactory.getLogger(PaymentApplicationService.class);

    private final PaymentAttemptRepository attemptRepository;
    private final List<PaymentGateway> gateways;
    private final OrderApplicationService orderService;
    private final ParentApplicationService parentService;
    private final SettlementApplicationService settlementService;
    private final MessagingApplicationService messagingService;
    private final AuditService auditService;

    public PaymentApplicationService(PaymentAttemptRepository attemptRepository,
                                     List<PaymentGateway> gateways,
                                     OrderApplicationService orderService,
                                     ParentApplicationService parentService,
                                     SettlementApplicationService settlementService,
                                     MessagingApplicationService messagingService,
                                     AuditService auditService) {
        this.attemptRepository = attemptRepository;
        this.gateways = gateways;
        this.orderService = orderService;
        this.parentService = parentService;
        this.settlementService = settlementService;
        this.messagingService = messagingService;
        this.auditService = auditService;
    }

    @Transactional
    public InitiatedPayment initiate(UUID orderId, String channelName) {
        OrderSnapshot order = orderService.getSnapshot(orderId);
        // 对象级授权：仅订单付款方/学生本人或运营可发起支付
        var caller = com.nurture.shared.security.CallerContext.require();
        boolean allowed = caller.role() == com.nurture.shared.security.CallerRole.ADMIN
                || order.getPayerParentId().equals(caller.accountId())
                || order.getStudentId().equals(caller.accountId());
        if (!allowed) {
            throw new org.springframework.security.access.AccessDeniedException("仅订单付款方或运营可发起支付");
        }
        if (!"PENDING_PAYMENT".equals(order.getStatus())) {
            throw new IllegalStateException("订单当前状态不可发起支付: " + order.getStatus());
        }
        PaymentAttempt.PaymentChannel channel = parseChannel(channelName);
        attemptRepository.findByOrderIdAndChannel(orderId, channel)
                .filter(a -> a.getStatus() == PaymentAttempt.AttemptStatus.INITIATED)
                .ifPresent(a -> {
                    throw new IllegalStateException("该订单已存在进行中的支付尝试，请勿重复发起");
                });
        PaymentAttempt attempt = new PaymentAttempt(orderId, channel, order.getAmountCents());
        PaymentGateway gateway = gatewayOf(channel);
        attempt.attachChannelRef(gateway.createPayment(orderId, order.getAmountCents(), "Nurture课时费"));
        PaymentAttempt saved = attemptRepository.save(attempt);
        return new InitiatedPayment(saved.getId(), saved.getChannel().name(), saved.getChannelRef(),
                saved.getAmountCents());
    }

    /** 支付成功联动：扣减家庭钱包 → 订单已付 → 生成课酬结算 → 双向通知。 */
    @Transactional
    public void executeSuccess(UUID orderId, String channelName, String payload, String signature) {
        String safeChannel = channelName == null || channelName.isBlank() ? "MOCK" : channelName;
        PaymentAttempt attempt = attemptRepository.findByOrderIdAndChannel(orderId,
                        com.nurture.payment.domain.PaymentAttempt.PaymentChannel.valueOf(safeChannel))
                .orElseThrow(() -> new IllegalArgumentException("支付尝试不存在"));
        PaymentGateway gateway = gatewayOf(attempt.getChannel());
        // 凭据校验：签名真实性 + 时效防重放 + 订单/金额一致性；失败即拒绝处理
        if (!gateway.verifyCallback(payload, signature, orderId, attempt.getAmountCents())) {
            auditService.record("payment.callback-rejected", "REJECTED",
                    Map.of("orderId", orderId.toString(), "channel", safeChannel),
                    "支付回调凭据校验失败，已拒绝处理");
            throw new UntrustedCallbackException("支付回调凭据校验失败，已拒绝处理");
        }
        // 回调防重入：唯一(channel, channelRef) + 幂等状态机
        attempt.succeed(sha256(payload));
        OrderSnapshot order = orderService.markPaidSnapshot(orderId);
        parentService.chargeForOrder(order.getPayerParentId(), orderId, order.getAmountCents(), "课时费支付 " + order.getOrderNo());
        settlementService.createEntryForPaidOrder(order);
        messagingService.notify(order.getTeacherId(), "SETTLEMENT",
                "课酬入账提醒", "订单 " + order.getOrderNo() + " 已完成支付，课酬进入待结算余额。");
        messagingService.notify(order.getPayerParentId(), "PAYMENT",
                "支付成功", "订单 " + order.getOrderNo() + " 支付成功，感谢您对因材施教的支持。");
        log.info("支付成功联动完成 orderId={}", orderId);
    }

    /** 支付失败回执：同样须通过网关凭据校验，防止伪造失败回执污染支付状态机。 */
    @Transactional
    public void executeFailure(UUID orderId, String channelName, String payload, String signature) {
        String safeChannel = channelName == null || channelName.isBlank() ? "MOCK" : channelName;
        PaymentAttempt attempt = attemptRepository.findByOrderIdAndChannel(orderId,
                        com.nurture.payment.domain.PaymentAttempt.PaymentChannel.valueOf(safeChannel))
                .orElseThrow(() -> new IllegalArgumentException("支付尝试不存在"));
        PaymentGateway gateway = gatewayOf(attempt.getChannel());
        if (!gateway.verifyCallback(payload, signature, orderId, attempt.getAmountCents())) {
            auditService.record("payment.callback-rejected", "REJECTED",
                    Map.of("orderId", orderId.toString(), "channel", safeChannel),
                    "失败回执凭据校验失败，已拒绝处理");
            throw new UntrustedCallbackException("支付失败回执凭据校验失败，已拒绝处理");
        }
        attempt.fail();
        log.info("支付失败回执已受理 orderId={}", orderId);
    }

    /**
     * 沙箱网关模拟：以网关身份组包并签名通知，再走与真实回调完全相同的校验与联动链路。
     * 仅在 nurture.payment.sandbox.enabled=true 时暴露，生产环境整体下线。
     */
    @Transactional
    public Map<String, String> sandboxNotify(UUID orderId, String channelName, boolean success) {
        String safeChannel = channelName == null || channelName.isBlank() ? "MOCK" : channelName;
        PaymentAttempt attempt = attemptRepository.findByOrderIdAndChannel(orderId,
                        com.nurture.payment.domain.PaymentAttempt.PaymentChannel.valueOf(safeChannel))
                .orElseThrow(() -> new IllegalArgumentException("支付尝试不存在"));
        if (!(gatewayOf(attempt.getChannel()) instanceof SimulatedPaymentGateway sandbox)) {
            throw new IllegalStateException("当前渠道未启用沙箱模拟: " + safeChannel);
        }
        var notification = sandbox.buildNotification(orderId, attempt.getChannelRef(),
                attempt.getAmountCents(), success ? "SUCCESS" : "FAILED");
        if (success) {
            executeSuccess(orderId, safeChannel, notification.payload(), notification.signature());
            return Map.of("result", "SUCCESS");
        }
        executeFailure(orderId, safeChannel, notification.payload(), notification.signature());
        return Map.of("result", "FAILED_ACCEPTED");
    }

    /**
     * 运营线下收款确认（上线模式 B）——运营核验银行转账/收款码到账后将订单标记为已付。
     * 与网关回调链路的差异：资金不经平台网关，凭运营操作员核验落账（无验签环节），
     * 因此不扣减家庭钱包（钱包充值/扣减仅适用于平台内资金流）。
     * 幂等：同订单已有成功 OFFLINE 尝试时直接返回既有结果。
     */
    @Transactional
    public Map<String, Object> confirmOfflinePayment(UUID orderId) {
        OrderSnapshot current = orderService.getSnapshot(orderId);
        if ("PAID".equals(current.getStatus())) {
            // 幂等返回与首次成功响应字段对齐（含 amountCents），便于客户端与测试做一致性断言
            return Map.of("orderId", orderId.toString(), "status", "PAID",
                    "channel", PaymentAttempt.PaymentChannel.OFFLINE.name(),
                    "amountCents", current.getAmountCents(), "idempotent", true);
        }
        if (!"PENDING_PAYMENT".equals(current.getStatus())) {
            throw new IllegalStateException("订单当前状态不可确认线下收款: " + current.getStatus());
        }
        PaymentAttempt attempt = attemptRepository
                .findByOrderIdAndChannel(orderId, PaymentAttempt.PaymentChannel.OFFLINE)
                .orElseGet(() -> {
                    PaymentAttempt created = new PaymentAttempt(orderId,
                            PaymentAttempt.PaymentChannel.OFFLINE, current.getAmountCents());
                    created.attachChannelRef("OFFLINE-" + UUID.randomUUID());
                    return created;
                });
        if (attempt.getStatus() != PaymentAttempt.AttemptStatus.SUCCEEDED) {
            attempt.succeed(sha256("OFFLINE-CONFIRMED:" + orderId));
            attemptRepository.save(attempt);
        }
        OrderSnapshot order = orderService.markPaidSnapshot(orderId);
        settlementService.createEntryForPaidOrder(order);
        messagingService.notify(order.getTeacherId(), "SETTLEMENT",
                "课酬入账提醒", "订单 " + order.getOrderNo() + " 支付已确认，课酬进入待结算余额。");
        messagingService.notify(order.getPayerParentId(), "PAYMENT",
                "支付成功", "订单 " + order.getOrderNo() + " 支付已确认，感谢你对因材施教的支持。");
        auditService.record("payment.offline-confirm", "SUCCESS",
                Map.of("orderId", orderId.toString(), "amountCents", order.getAmountCents()),
                "运营线下收款确认（资金不经平台网关）");
        log.info("线下收款确认完成 orderId={}", orderId);
        return Map.of("orderId", orderId.toString(), "status", order.getStatus(),
                "channel", PaymentAttempt.PaymentChannel.OFFLINE.name(), "amountCents", order.getAmountCents());
    }

    private PaymentGateway gatewayOf(PaymentAttempt.PaymentChannel channel) {
        return gateways.stream()
                .filter(g -> g.channelName().equalsIgnoreCase(channel.name()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("未接入支付渠道: " + channel));
    }

    private PaymentAttempt.PaymentChannel parseChannel(String name) {
        try {
            return PaymentAttempt.PaymentChannel.valueOf(name == null ? "MOCK" : name.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("不支持的支付渠道: " + name);
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public record InitiatedPayment(UUID attemptId, String channel, String channelRef, long amountCents) {
    }
}
