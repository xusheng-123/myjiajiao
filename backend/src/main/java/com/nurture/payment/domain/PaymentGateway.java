package com.nurture.payment.domain;

import java.util.UUID;

/**
 * 外部支付渠道端口（六边形架构 Port）。
 * 渠道适配器必须实现：支付单创建、异步通知验签（HMAC + 订单/金额/时效一致性与时间安全比较）、
 * 退款与余额查询。接入真实渠道（微信/支付宝）时新增适配器并注入渠道密钥，业务层零改动。
 */
public interface PaymentGateway {

    /** 渠道标识，与 PaymentAttempt.PaymentChannel 枚举名一致。 */
    String channelName();

    /** 创建支付单，返回渠道支付引用（channelRef）。 */
    String createPayment(UUID orderId, long amountCents, String subject);

    /**
     * 校验异步通知：签名真实性（时间安全比较）、时效（防重放）、
     * 订单与金额与本平台记录一致。任一不满足即拒绝。
     *
     * @param payload              渠道原始通知报文（JSON）
     * @param signature            渠道签名
     * @param orderId              本平台订单 ID
     * @param expectedAmountCents  本平台记录的应付金额（分）
     */
    boolean verifyCallback(String payload, String signature, UUID orderId, long expectedAmountCents);
}
