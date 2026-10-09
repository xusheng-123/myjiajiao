package com.nurture.shared.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseCookie;

import java.util.Arrays;

/**
 * 浏览器会话 Cookie 支持（D5 最小迁移）：
 * <ul>
 *   <li>访问令牌 Cookie（HttpOnly）— JS 不可读，替代 localStorage 中的 accessToken；</li>
 *   <li>刷新令牌 Cookie（HttpOnly）— 仅回传给鉴权端点（Path 限定），JS 不可读。</li>
 * </ul>
 * 安全属性：HttpOnly；生产 HTTPS 下 SameSite=None; Secure（Cloudflare Pages 跨源调用需 credentials）；
 * 本地同源 http 下 SameSite=Lax。CSRF 防护依赖：业务写操作强制自定义 Idempotency-Key 报头
 * （跨站表单无法携带）+ CORS 白名单，两者与 Cookie 双重隔离。
 * Agent API 不受影响：Agent 仍以 Authorization: Bearer nur_at_* 走独立通道。
 */
public final class AuthCookies {

    public static final String ACCESS_COOKIE = "NURTURE_AT";
    public static final String REFRESH_COOKIE = "NURTURE_RT";
    private static final String ACCESS_PATH = "/api";
    private static final String REFRESH_PATH = "/api/v1/identity/auth";

    private AuthCookies() {
    }

    /** 登录/注册/刷新成功后写入浏览器会话 Cookie。 */
    public static void attach(HttpServletResponse response, HttpServletRequest request,
                              String accessToken, String refreshToken) {
        boolean secure = request.isSecure();
        String sameSite = secure ? "None" : "Lax";
        response.addHeader("Set-Cookie", ResponseCookie.from(ACCESS_COOKIE, accessToken)
                .httpOnly(true).secure(secure).sameSite(sameSite)
                .path(ACCESS_PATH).maxAge(1800).build().toString());
        response.addHeader("Set-Cookie", ResponseCookie.from(REFRESH_COOKIE, refreshToken)
                .httpOnly(true).secure(secure).sameSite(sameSite)
                .path(REFRESH_PATH).maxAge(14 * 24 * 3600).build().toString());
    }

    /** 退出登录：清除浏览器会话 Cookie（服务端刷新令牌吊销由业务完成）。 */
    public static void clear(HttpServletResponse response, HttpServletRequest request) {
        boolean secure = request.isSecure();
        String sameSite = secure ? "None" : "Lax";
        response.addHeader("Set-Cookie", ResponseCookie.from(ACCESS_COOKIE, "")
                .httpOnly(true).secure(secure).sameSite(sameSite)
                .path(ACCESS_PATH).maxAge(0).build().toString());
        response.addHeader("Set-Cookie", ResponseCookie.from(REFRESH_COOKIE, "")
                .httpOnly(true).secure(secure).sameSite(sameSite)
                .path(REFRESH_PATH).maxAge(0).build().toString());
    }

    /** 从 Cookie 中取访问令牌（供统一认证过滤器使用）。 */
    public static String accessFrom(HttpServletRequest request) {
        return cookieValue(request, ACCESS_COOKIE);
    }

    /** 从 Cookie 中取刷新令牌（供刷新/登出端点在请求体缺省时使用）。 */
    public static String refreshFrom(HttpServletRequest request) {
        return cookieValue(request, REFRESH_COOKIE);
    }

    private static String cookieValue(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        return Arrays.stream(cookies)
                .filter(c -> name.equals(c.getName()))
                .map(Cookie::getValue)
                .filter(v -> !v.isBlank())
                .findFirst()
                .orElse(null);
    }
}
