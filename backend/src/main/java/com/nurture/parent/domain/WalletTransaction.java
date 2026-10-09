package com.nurture.parent.domain;

import com.nurture.shared.domain.AggregateRoot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * 课时消费对账明细 — 钱包每一笔资金变动的不可变流水。
 */
@Entity
@Table(name = "wallet_transactions", indexes = {
        @Index(name = "ix_wallet_txn_wallet", columnList = "walletId, createdAt"),
        @Index(name = "ix_wallet_txn_related", columnList = "relatedOrderId")
})
public class WalletTransaction extends AggregateRoot {

    public enum TxnType { TOPUP, PAYMENT, REFUND, ADJUSTMENT }

    @Column(name = "wallet_id", nullable = false)
    private UUID walletId;

    @Enumerated(EnumType.STRING)
    @Column(name = "txn_type", nullable = false, length = 16)
    private TxnType type;

    @Column(name = "amount_cents", nullable = false)
    private long amountCents;

    @Column(name = "balance_after_cents", nullable = false)
    private long balanceAfterCents;

    @Column(name = "related_order_id")
    private UUID relatedOrderId;

    @Column(name = "memo", length = 256)
    private String memo;

    protected WalletTransaction() {
    }

    public WalletTransaction(UUID walletId, TxnType type, long amountCents, long balanceAfterCents,
                             UUID relatedOrderId, String memo) {
        this.walletId = walletId;
        this.type = type;
        this.amountCents = amountCents;
        this.balanceAfterCents = balanceAfterCents;
        this.relatedOrderId = relatedOrderId;
        this.memo = memo;
    }

    public UUID getWalletId() {
        return walletId;
    }

    public TxnType getType() {
        return type;
    }

    public long getAmountCents() {
        return amountCents;
    }

    public long getBalanceAfterCents() {
        return balanceAfterCents;
    }

    public UUID getRelatedOrderId() {
        return relatedOrderId;
    }

    public String getMemo() {
        return memo;
    }
}
