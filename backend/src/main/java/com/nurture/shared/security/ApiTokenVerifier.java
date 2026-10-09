package com.nurture.shared.security;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Agent API Token 校验端口 — 由 identity 模块实现注册。
 * Token 仅在生成时刻展示明文一次，数据库中仅存储不可逆加盐哈希。
 */
public interface ApiTokenVerifier {

    Optional<Verification> verify(String rawToken);

    record Verification(UUID accountId, String displayName, Set<Scope> scopes) {
    }
}
