package com.nurture.payment.infra;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nurture.payment.domain.PaymentGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 沙箱支付网关适配器 — 渠道名 MOCK，本地/联调环境使用。
 * 与"恒真校验"的空壳 Mock 不同：本适配器按真实渠道的通知协议实现
 * HMAC-SHA256 签名、时间安全比较、时效（防重放）与订单/金额一致性校验，
 * 密钥只存在于网关侧（nurture.payment.sandbox.secret），商户侧仅做验签。
 * 切换真实渠道（微信/支付宝）时按同一端口协议新增适配器，业务层零改动。
 */
@Component
public class SimulatedPaymentGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(SimulatedPaymentGateway.class);
    private static final String HMAC_SHA256 = "HmacSHA256";
    /** 通知时效窗口：超出视为重放攻击。 */
    private static final long MAX_CLOCK_DRIFT_SECONDS = 600;

    private final String hmacSecret;
    private final ObjectMapper objectMapper;

    public SimulatedPaymentGateway(PaymentProperties properties, ObjectMapper objectMapper) {
        this.hmacSecret = properties.sandboxOrDefault().hmacSecret();
        this.objectMapper = objectMapper;
    }

    @Override
    public String channelName() {
        return "MOCK";
    }

    @Override
    public String createPayment(UUID orderId, long amountCents, String subject) {
        log.info("[SandboxGateway] 创建支付单 order={}, amount={}, subject={}", orderId, amountCents, subject);
        return "MOCK-PAY-" + UUID.randomUUID();
    }

    /** 渠道支付引用 → 沙箱异步通知报文（等价真实渠道回调组包）。 */
    public record GatewayNotification(String payload, String signature) {
    }

    /**
     * 网关侧组包并签名 — 仅沙箱模拟端点使用，等价于真实渠道回调里的报文与签名。
     * 字段顺序固定（LinkedHashMap），商户侧验签可复现。
     */
    public GatewayNotification buildNotification(UUID orderId, String channelRef, long amountCents, String status) {
        Map<String, Object> notification = new LinkedHashMap<>();
        notification.put("orderId", orderId.toString());
        notification.put("channelRef", channelRef);
        notification.put("amountCents", amountCents);
        notification.put("status", status);
        notification.put("timestamp", Instant.now().toEpochMilli());
        notification.put("nonce", UUID.randomUUID().toString());
        try {
            String payload = objectMapper.writeValueAsString(notification);
            return new GatewayNotification(payload, sign(payload));
        } catch (Exception e) {
            throw new IllegalStateException("沙箱网关通知组包失败", e);
        }
    }

    @Override
    public boolean verifyCallback(String payload, String signature, UUID orderId, long expectedAmountCents) {
        if (payload == null || payload.isBlank() || signature == null || signature.isBlank()) {
            return false;
        }
        // ① 签名真实性：时间安全比较，防时序侧信道
        if (!MessageDigest.isEqual(sign(payload).getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8))) {
            log.warn("[SandboxGateway] 回调签名校验失败");
            return false;
        }
        try {
            JsonNode node = objectMapper.readTree(payload);
            // ② 时效：超出 10 分钟窗口的通知视为重放
            long timestamp = node.path("timestamp").asLong(0);
            if (Math.abs(Instant.now().toEpochMilli() - timestamp) > MAX_CLOCK_DRIFT_SECONDS * 1000) {
                log.warn("[SandboxGateway] 回调超出时效窗口（可能重放）timestamp={}", timestamp);
                return false;
            }
            // ③ 一致性：订单与本平台记录一致
            if (!node.path("orderId").asText("").equals(orderId.toString())) {
                log.warn("[SandboxGateway] 回调订单不一致 received={} expected={}",
                        node.path("orderId").asText(""), orderId);
                return false;
            }
            // ④ 一致性：金额与本平台应付一致（防篡改少付多发）
            if (node.path("amountCents").asLong(Long.MIN_VALUE) != expectedAmountCents) {
                log.warn("[SandboxGateway] 回调金额不一致 expected={}", expectedAmountCents);
                return false;
            }
            // ⑤ 状态语义：仅 SUCCESS 视为支付成功
            return "SUCCESS".equals(node.path("status").asText(""));
        } catch (Exception e) {
            log.warn("[SandboxGateway] 回调报文解析失败");
            return false;
        }
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(hmacSecret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC 签名计算失败", e);
        }
    }
}
