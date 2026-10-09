package com.nurture.shared.security;

import java.util.Set;
import java.util.UUID;

/**
 * 已鉴权调用者 — 由 JWT 或 Agent API Token 解析而来。
 *
 * @param accountId   账户 ID（Agent 为其 Service Account 的 ID）
 * @param role        调用者角色
 * @param displayName 展示名
 * @param scopes      权限作用域（人类用户按角色展开，Agent 按 Token 声明）
 */
public record CallerPrincipal(UUID accountId, CallerRole role, String displayName, Set<Scope> scopes) {

    public boolean has(Scope scope) {
        return scopes.contains(scope);
    }

    public boolean is(CallerRole... roles) {
        for (CallerRole r : roles) {
            if (role == r) {
                return true;
            }
        }
        return false;
    }
}
