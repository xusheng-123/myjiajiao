package com.nurture.payment.api;

import com.nurture.payment.app.PaymentApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 支付网关 API — 发起支付与异步回调（回调端点免鉴权，靠凭据校验 + 防重入保护）。
 */
@RestController
@RequestMapping("/api/v1/payment")
@Tag(name = "payment / 支付网关", description = "外部支付渠道适配、凭据校验与回调防重入")
public class PaymentController {

    private final PaymentApplicationService paymentService;

    public PaymentController(PaymentApplicationService paymentService) {
        this.paymentService = paymentService;
    }

    public record InitiatePaymentRequest(@NotNull UUID orderId, String channel) {
    }

    public record InitiatedPaymentResponse(UUID attemptId, String channel, String channelRef, long amountCents,
                                           String notice) {
    }

    public record GatewayCallbackRequest(@NotNull UUID orderId, String channel, String payload, String signature,
                                         boolean success) {
    }

    @PostMapping("/payments:initiate")
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirement(name = "bearerJWT")
    @Operation(summary = "发起支付", description = "为待支付订单创建支付尝试，返回渠道支付引用")
    public InitiatedPaymentResponse initiate(@RequestBody InitiatePaymentRequest request) {
        var initiated = paymentService.initiate(request.orderId(), request.channel());
        return new InitiatedPaymentResponse(initiated.attemptId(), initiated.channel(), initiated.channelRef(),
                initiated.amountCents(), "请在前端完成支付后等待回调确认；回调具备防重入保护。");
    }

    /**
     * 支付回调 — 真实渠道（接入后）经 Cloudflare Tunnel 映射到本端点。
     * 免 JWT 鉴权（SecurityConfig 已放行 /api/v1/payment/callbacks/**），
     * 由网关签名验签（HMAC + 时效 + 订单/金额一致性）+ 唯一约束防重入保护；
     * 凭据校验失败返回 401，成功回调重放返回 409。
     */
    @PostMapping("/callbacks/gateway")
    @Operation(summary = "支付渠道异步回调（服务端间）", description = "凭据校验失败即 401 拒绝；成功回调幂等防重入")
    public java.util.Map<String, String> gatewayCallback(@RequestBody GatewayCallbackRequest request) {
        if (request.success()) {
            paymentService.executeSuccess(request.orderId(), request.channel(),
                    request.payload() == null ? "" : request.payload(), request.signature());
            return java.util.Map.of("result", "SUCCESS");
        }
        paymentService.executeFailure(request.orderId(), request.channel(),
                request.payload() == null ? "" : request.payload(), request.signature());
        return java.util.Map.of("result", "FAILED_ACCEPTED");
    }
}
