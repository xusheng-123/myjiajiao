package com.nurture.parent.domain;

import com.nurture.shared.domain.AggregateRoot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * 家长独立实体账户 — 拥有独立支付钱包与监管看板，与学生实体物理分离。
 */
@Entity
@Table(name = "parent_profiles", indexes = @Index(name = "ux_parent_account", columnList = "accountId", unique = true))
public class ParentProfile extends AggregateRoot {

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "parent_no", nullable = false, length = 24)
    private String parentNo;

    @Column(name = "contact_name", length = 64)
    private String contactName;

    protected ParentProfile() {
    }

    public ParentProfile(UUID accountId, String parentNo) {
        this.accountId = accountId;
        this.parentNo = parentNo;
    }

    public void rename(String contactName) {
        if (contactName != null && !contactName.isBlank()) {
            this.contactName = contactName;
        }
    }

    public UUID getAccountId() {
        return accountId;
    }

    public String getParentNo() {
        return parentNo;
    }

    public String getContactName() {
        return contactName;
    }
}
