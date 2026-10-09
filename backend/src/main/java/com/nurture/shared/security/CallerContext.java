package com.nurture.shared.security;

import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 调用者上下文 — 从 Spring Security 上下文读取当前 CallerPrincipal。
 */
public final class CallerContext {

    private CallerContext() {
    }

    public static CallerPrincipal require() {
        CallerPrincipal principal = get();
        if (principal == null) {
            throw new IllegalStateException("鉴权上下文缺失：当前请求未携带有效凭证");
        }
        return principal;
    }

    public static CallerPrincipal get() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CallerPrincipal p) {
            return p;
        }
        return null;
    }
}
