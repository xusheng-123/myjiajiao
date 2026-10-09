package com.nurture.shared.audit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Agent 写操作全量审计过滤器 — 所有 AI Agent 发起的非 GET 请求强制落不可篡改审计
 * （PRD 验收 10.2：所有 Agent 写操作进入审计日志；失败和重试不产生重复订单/预约由幂等过滤器保障）。
 */
@Component
@Order(80)
public class AgentWriteAuditFilter extends OncePerRequestFilter {

    private final AuditLogRepository auditLogRepository;

    public AgentWriteAuditFilter(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return switch (request.getMethod()) {
            case "POST", "PUT", "PATCH", "DELETE" -> false;
            default -> true;
        };
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean isAgent = authentication != null
                && authentication.getPrincipal() instanceof com.nurture.shared.security.CallerPrincipal p
                && p.role() == com.nurture.shared.security.CallerRole.AGENT;

        chain.doFilter(request, response);

        if (isAgent) {
            var principal = (com.nurture.shared.security.CallerPrincipal) authentication.getPrincipal();
            String status = response.getStatus() < 400 ? "SUCCESS" : "FAILED";
            auditLogRepository.save(new AuditLog(
                    principal.accountId(),
                    "AGENT",
                    "agent:" + principal.accountId(),
                    java.time.Instant.now(),
                    request.getMethod(), request.getRequestURI(),
                    "agent:write",
                    null,
                    status,
                    "Agent 写操作自动审计（HTTP " + response.getStatus() + "）"));
        }
    }
}
