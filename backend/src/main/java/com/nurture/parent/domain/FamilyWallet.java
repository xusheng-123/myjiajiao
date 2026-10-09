package com.nurture.parent.domain;

import com.nurture.shared.domain.AggregateRoot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.util.UUID;

/**
 * 家庭统一教育支付钱包 — 每位家长一个钱包，乐观锁并发控制。
 */
@Entity
@Table(name = "family_wallets", indexes = @Index(name = "ux_wallet_parent", columnList = "parentId", unique = true))
public class FamilyWallet extends AggregateRoot {

    @Column(name = "parent_id", nullable = false)
    private UUID parentId;

    @Column(name = "balance_cents", nullable = false)
    private long balanceCents = 0;

    @Column(name = "currency", nullable = false, length = 8)
    private String currency = "CNY";

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected FamilyWallet() {
    }

    public FamilyWallet(UUID parentId) {
        this.parentId = parentId;
    }

    public void credit(long amountCents) {
        if (amountCents <= 0) {
            throw new IllegalArgumentException("入账金额必须为正");
        }
        this.balanceCents += amountCents;
    }

    public void debit(long amountCents) {
        if (amountCents <= 0) {
            throw new IllegalArgumentException("扣减金额必须为正");
        }
        if (this.balanceCents < amountCents) {
            throw new IllegalStateException("钱包余额不足");
        }
        this.balanceCents -= amountCents;
    }

    public UUID getParentId() {
        return parentId;
    }

    public long getBalanceCents() {
        return balanceCents;
    }

    public String getCurrency() {
        return currency;
    }
}
