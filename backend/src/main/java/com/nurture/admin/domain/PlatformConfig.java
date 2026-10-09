package com.nurture.admin.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 平台全局参数配置 — 运营治理控制台可动态调整（如抽成比例）。
 */
@Entity
@Table(name = "platform_configs")
public class PlatformConfig {

    @Id
    @Column(name = "config_key", length = 64, nullable = false)
    private String key;

    @Column(name = "config_value", nullable = false, length = 256)
    private String value;

    @Column(name = "description", length = 256)
    private String description;

    @Column(name = "updated_by", length = 64)
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected PlatformConfig() {
    }

    public PlatformConfig(String key, String value, String description) {
        this.key = key;
        this.value = value;
        this.description = description;
    }

    public void update(String value, String updatedBy) {
        this.value = value;
        this.updatedBy = updatedBy;
        this.updatedAt = Instant.now();
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    public String getDescription() {
        return description;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
