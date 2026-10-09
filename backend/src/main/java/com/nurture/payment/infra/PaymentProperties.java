package com.nurture.payment.infra;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 支付网关配置属性。
 * sandbox.enabled=false 时（生产）沙箱网关模拟端点整体下线，
 * 仅保留签名校验的正式回调端点等待真实渠道接入。
 */
@ConfigurationProperties(prefix = "nurture.payment")
public record PaymentProperties(String defaultChannel, Sandbox sandbox) {

    /** 沙箱网关：模拟真实渠道的异步通知行为（签名回执），供联调与演示。 */
    public record Sandbox(boolean enabled, String secret) {

        public String hmacSecret() {
            return secret == null || secret.isBlank()
                    ? "dev-sandbox-secret-nurture-0123456789abcdef" : secret;
        }
    }

    public Sandbox sandboxOrDefault() {
        return sandbox == null ? new Sandbox(true, null) : sandbox;
    }
}
