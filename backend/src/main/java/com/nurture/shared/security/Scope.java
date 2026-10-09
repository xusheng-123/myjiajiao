package com.nurture.shared.security;

/**
 * 细粒度权限作用域 — 最小权限原则。
 * Agent 专用 Token 必须显式声明 Scope，无权限请求就地拦截熔断。
 */
public enum Scope {
    STUDENT_READ("student:read"),
    STUDENT_WRITE("student:write"),
    PARENT_READ("parent:read"),
    PARENT_WRITE("parent:write"),
    TEACHER_READ("teacher:read"),
    TEACHER_WRITE("teacher:write"),
    MATCHING_READ("matching:read"),
    SCHEDULING_READ("scheduling:read"),
    SCHEDULING_WRITE("scheduling:write"),
    BOOKING_READ("booking:read"),
    BOOKING_CREATE("booking:create"),
    BOOKING_CANCEL("booking:cancel"),
    CLASSROOM_JOIN("classroom:join"),
    ORDER_READ("order:read"),
    ORDER_CREATE("order:create"),
    PAYMENT_READ("payment:read"),
    REFUND_READ("refund:read"),
    REFUND_REQUEST("refund:request"),
    REVIEW_READ("review:read"),
    REVIEW_WRITE("review:write"),
    MESSAGING_READ("messaging:read"),
    MESSAGING_WRITE("messaging:write"),
    ADMIN_READ("admin:read"),
    ADMIN_WRITE("admin:write"),
    AUDIT_READ("audit:read");

    private final String code;

    Scope(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    /** 兼容 "teacher:read" 形式的解析，未匹配返回 null。 */
    public static Scope fromCode(String code) {
        for (Scope s : values()) {
            if (s.code.equals(code)) {
                return s;
            }
        }
        return null;
    }
}
