package com.nurture.identity.domain;

import com.nurture.shared.domain.AggregateRoot;
import com.nurture.shared.security.CallerRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * 统一账号 — 学生、家长、导师、运营为彼此独立的账户实体，严禁共用登录模型。
 */
@Entity
@Table(name = "accounts",
        indexes = {
                @Index(name = "ux_accounts_phone", columnList = "phone", unique = true),
                @Index(name = "ux_accounts_account_no", columnList = "accountNo", unique = true)
        })
public class Account extends AggregateRoot {

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 16)
    private CallerRole role;

    @Column(name = "account_no", nullable = false, length = 24)
    private String accountNo;

    @Column(name = "phone", nullable = false, length = 16)
    private String phone;

    @Column(name = "display_name", nullable = false, length = 64)
    private String displayName;

    /** BCrypt 单向散列，严禁存储明文。 */
    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private AccountStatus status = AccountStatus.ACTIVE;

    protected Account() {
    }

    public Account(CallerRole role, String accountNo, String phone, String displayName, String passwordHash) {
        this.role = role;
        this.accountNo = accountNo;
        this.phone = phone;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
    }

    public boolean passwordMatches(String raw, org.springframework.security.crypto.password.PasswordEncoder encoder) {
        return passwordHash != null && encoder.matches(raw, passwordHash);
    }

    public CallerRole getRole() {
        return role;
    }

    public String getAccountNo() {
        return accountNo;
    }

    public String getPhone() {
        return phone;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public void lock() {
        this.status = AccountStatus.LOCKED;
    }

    public void unlock() {
        this.status = AccountStatus.ACTIVE;
    }
}
