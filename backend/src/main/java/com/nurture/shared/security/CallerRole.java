package com.nurture.shared.security;

/**
 * 调用者角色 — 家长与学生是两个彼此独立的账户实体，严禁混淆。
 */
public enum CallerRole {
    STUDENT,
    PARENT,
    TEACHER,
    ADMIN,
    /** AI Agent 独立 Service Account，专用 API Token 认证，严禁模拟人类浏览器会话。 */
    AGENT
}
