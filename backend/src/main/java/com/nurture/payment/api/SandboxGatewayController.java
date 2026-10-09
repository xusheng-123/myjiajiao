package com.nurture.payment.api;

import com.nurture.payment.app.PaymentApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * 沙箱支付网关模拟端点 — 扮演"外部渠道"角色：模拟用户在渠道侧完成/取消支付后，
 * 渠道向平台异步通知的行为。通知由沙箱网关以渠道密钥组包签名，随后走与真实回调
 * 完全相同的验签与联动链路（验签失败照样 401 拒绝）。
 * 生产环境设 nurture.payment.sandbox.enabled=false，本端点整体下线。
 */
@RestController
@RequestMapping("/api/v1/payment/sandbox")
@ConditionalOnProperty(name = "nurture.payment.sandbox.enabled", havingValue = "true", matchIfMissing = true)
@Tag(name = "payment / 沙箱网关（联调专用）",
        description = "模拟外部渠道异步通知；生产环境自动下线，勿在生产开放")
public class SandboxGatewayController {

    private final PaymentApplicationService paymentService;

    public SandboxGatewayController(PaymentApplicationService paymentService) {
        this.paymentService = paymentService;
    }

    public record SandboxNotifyRequest(@NotNull UUID orderId, String channel, boolean success) {
    }

    @PostMapping("/notify")
    @Operation(summary = "模拟渠道异步通知", description = "以网关身份对支付结果签名并回传平台；"
            + "等价真实渠道回调，验签、防重入、金额一致性全部生效")
    public Map<String, String> notify(@RequestBody SandboxNotifyRequest request) {
        return paymentService.sandboxNotify(request.orderId(), request.channel(), request.success());
    }
}
