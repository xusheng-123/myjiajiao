package com.nurture.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * 双主权客户端鉴权过滤器：
 * 人类 Web 客户端 → Bearer JWT；AI Agent 客户端 → 独立 API Token（nur_at_ 前缀）。
 * 两者共享同一套 /api/v1/* 契约与权限模型。
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final Optional<ApiTokenVerifier> apiTokenVerifier;
    private final Optional<AccountStatusVerifier> accountStatusVerifier;

    public JwtAuthenticationFilter(JwtService jwtService,
                                   Optional<ApiTokenVerifier> apiTokenVerifier,
                                   Optional<AccountStatusVerifier> accountStatusVerifier) {
        this.jwtService = jwtService;
        this.apiTokenVerifier = apiTokenVerifier;
        this.accountStatusVerifier = accountStatusVerifier;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        // 凭证来源优先级：Authorization 头（API/Agent）→ 浏览器会话 Cookie（D5 Web 迁移，HttpOnly JS 不可读）
        String credential = null;
        if (header != null && header.startsWith("Bearer ")) {
            credential = header.substring(7).trim();
        } else if (request.getCookies() != null) {
            credential = com.nurture.shared.security.AuthCookies.accessFrom(request);
        }
        if (credential != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                CallerPrincipal principal = resolve(credential);
                // 统一认证层状态闸门：锁定/注销账户的存量凭证在入口即被拒绝
                boolean usable = principal != null
                        && accountStatusVerifier.map(v -> v.active(principal.accountId())).orElse(true);
                if (principal != null && usable) {
                    var authorities = new java.util.ArrayList<SimpleGrantedAuthority>();
                    // 角色权威（@PreAuthorize hasRole）+ Scope 权威（hasAuthority SCOPE_xxx）
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + principal.role().name()));
                    principal.scopes().stream()
                            .map(s -> new SimpleGrantedAuthority("SCOPE_" + s.code()))
                            .forEach(authorities::add);
                    var authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (Exception ignored) {
                // 无效凭证按匿名处理，由授权规则统一拦截
            }
        }
        chain.doFilter(request, response);
    }

    private CallerPrincipal resolve(String credential) {
        if (credential.startsWith("ey")) {
            return jwtService.verify(credential);
        }
        return apiTokenVerifier.map(verifier -> verifier.verify(credential)
                        .map(v -> new CallerPrincipal(v.accountId(), CallerRole.AGENT, v.displayName(), v.scopes()))
                        .orElse(null))
                .orElse(null);
    }
}
