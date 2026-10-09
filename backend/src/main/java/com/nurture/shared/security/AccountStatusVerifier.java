package com.nurture.shared.security;

import java.util.UUID;

/**
 * 账户状态校验端口 — 统一认证层的会话有效性闸门。
 * 由 identity 模块实现；JWT 过滤器与 Agent Token 验证在每个请求上调用，
 * 直接读取账户当前状态（无缓存窗口）：运营锁定/注销后，存量 Access Token
 * 与 Agent Token 在下一次请求即被拒绝，无需在各 Controller 复制逻辑。
 */
public interface AccountStatusVerifier {

    /** 账户当前是否可用（ACTIVE）。每次请求直查数据库，无缓存窗口。 */
    boolean active(UUID accountId);
}
