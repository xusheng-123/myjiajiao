package com.nurture.shared.config;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * 平台全局参数读取端口 — admin 模块实现注册；其他模块经本端口读取，不直取其仓储。
 */
public interface PlatformConfigReader {

    Optional<String> get(String key);

    default long longValue(String key, long fallback) {
        return get(key).map(v -> {
            try {
                return Long.parseLong(v.trim());
            } catch (NumberFormatException e) {
                return fallback;
            }
        }).orElse(fallback);
    }

    default BigDecimal decimalValue(String key, BigDecimal fallback) {
        return get(key).map(v -> {
            try {
                return new BigDecimal(v.trim());
            } catch (NumberFormatException e) {
                return fallback;
            }
        }).orElse(fallback);
    }
}
