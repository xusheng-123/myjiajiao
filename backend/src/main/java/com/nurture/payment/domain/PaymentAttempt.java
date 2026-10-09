package com.nurture.payment.domain;

import com.nurture.shared.domain.AggregateRoot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * 支付尝试 — 外部渠道适配记录；回调以渠道引用唯一约束实现防重入。
 */
@Entity
@Table(name = "payment_attempts", indexes = {
        @Index(name = "ix_payment_order", columnList = "orderId"),
        @Index(name = "ux_payment_channel_ref", columnList = "channel, channelRef", unique = true)
})
public class PaymentAttempt extends AggregateRoot {

    /** OFFLINE：运营线下收款确认（渠道 B）——资金不经平台网关，凭运营操作员核验落账。 */
    public enum PaymentChannel { MOCK, OFFLINE, WECHAT, ALIPAY }

    public enum AttemptStatus { INITIATED, SUCCEEDED, FAILED }

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 16)
    private PaymentChannel channel;

    @Column(name = "channel_ref", length = 128)
    private String channelRef;

    @Column(name = "amount_cents", nullable = false)
    private long amountCents;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private AttemptStatus status = AttemptStatus.INITIATED;

    @Column(name = "callback_payload_hash", length = 64)
    private String callbackPayloadHash;

    protected PaymentAttempt() {
    }

    public PaymentAttempt(UUID orderId, PaymentChannel channel, long amountCents) {
        this.orderId = orderId;
        this.channel = channel;
        this.amountCents = amountCents;
    }

    public void attachChannelRef(String channelRef) {
        this.channelRef = channelRef;
    }

    public void succeed(String callbackPayloadHash) {
        if (status == AttemptStatus.SUCCEEDED) {
            throw new IllegalStateException("支付回调防重入：本笔支付已成功，拒绝重复处理");
        }
        this.status = AttemptStatus.SUCCEEDED;
        this.callbackPayloadHash = callbackPayloadHash;
    }

    /** 失败回执：仅 INITIATED 可流转；已成功的支付拒绝被失败回执污染（防状态回退）。 */
    public void fail() {
        if (status == AttemptStatus.SUCCEEDED) {
            throw new IllegalStateException("该笔支付已成功，拒绝失败回执");
        }
        this.status = AttemptStatus.FAILED;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public PaymentChannel getChannel() {
        return channel;
    }

    public String getChannelRef() {
        return channelRef;
    }

    public long getAmountCents() {
        return amountCents;
    }

    public AttemptStatus getStatus() {
        return status;
    }
}
