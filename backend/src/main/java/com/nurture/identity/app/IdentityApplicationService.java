package com.nurture.identity.app;

import com.nurture.identity.domain.Account;
import com.nurture.identity.domain.AccountStatus;
import com.nurture.identity.domain.ApiToken;
import com.nurture.identity.domain.RefreshToken;
import com.nurture.identity.infra.AccountRepository;
import com.nurture.identity.infra.ApiTokenRepository;
import com.nurture.identity.infra.RefreshTokenRepository;
import com.nurture.shared.outbox.OutboxWriter;
import com.nurture.shared.security.AccountStatusVerifier;
import com.nurture.shared.security.ApiTokenVerifier;
import com.nurture.shared.security.CallerPrincipal;
import com.nurture.shared.security.CallerRole;
import com.nurture.shared.security.JwtService;
import com.nurture.shared.security.Scope;
import com.nurture.shared.security.SecurityProperties;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 统一账号应用服务 — 注册、双通道登录、JWT 会话、Agent API Token 生命周期。
 * 同时实现 {@link AccountStatusVerifier}：统一认证层的账户状态闸门（锁定/注销后
 * 存量 Access Token 与 Agent Token 在下一次请求即被拒绝，无缓存窗口）。
 */
@Service
public class IdentityApplicationService implements ApiTokenVerifier, AccountStatusVerifier {

    private static final Random RANDOM = new SecureRandom();

    private final AccountRepository accountRepository;
    private final ApiTokenRepository apiTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final SecurityProperties securityProperties;
    private final OutboxWriter outboxWriter;
    private final LoginThrottleService loginThrottleService;
    private final RegistrationRateLimiter registrationRateLimiter;

    public IdentityApplicationService(AccountRepository accountRepository,
                                      ApiTokenRepository apiTokenRepository,
                                      RefreshTokenRepository refreshTokenRepository,
                                      PasswordEncoder passwordEncoder,
                                      JwtService jwtService,
                                      SecurityProperties securityProperties,
                                      OutboxWriter outboxWriter,
                                      LoginThrottleService loginThrottleService,
                                      RegistrationRateLimiter registrationRateLimiter) {
        this.accountRepository = accountRepository;
        this.apiTokenRepository = apiTokenRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.securityProperties = securityProperties;
        this.outboxWriter = outboxWriter;
        this.loginThrottleService = loginThrottleService;
        this.registrationRateLimiter = registrationRateLimiter;
    }

    // ------------------------------------------------------------------ 注册

    @Transactional
    public Account register(CallerRole role, String displayName, String phone, String password, String clientIp) {
        if (accountRepository.findByPhone(phone).isPresent()) {
            throw new IllegalStateException("该手机号已注册，请直接登录");
        }
        Account account = new Account(role, nextAccountNo(role), phone, displayName,
                passwordEncoder.encode(password));
        Account saved = accountRepository.save(account);
        // 注册频率限制：仅成功注册消耗配额（防脚本批量注册垃圾账户）
        registrationRateLimiter.consume(clientIp);
        outboxWriter.write("identity.account-registered", "Account", saved.getId(),
                java.util.Map.of("role", role.name(), "displayName", displayName));
        return saved;
    }

    // ------------------------------------------------------------------ 登录

    /**
     * 密码登录。防暴力破解：先校验账号/IP 是否处于锁定期；密码错误计入失败计数，
     * 达到阈值触发临时锁定（账号维度 + IP 维度），成功登录清零计数。
     * clientIp 优先取 X-Forwarded-For 首跳（Cloudflare Tunnel 反代透传），缺省回落 remoteAddr。
     */
    @Transactional(readOnly = true)
    public Session loginByPassword(CallerRole role, String account, String password, String clientIp) {
        String identifier = account == null ? "" : account.trim().toLowerCase();
        loginThrottleService.checkAllowed(identifier, clientIp);
        Account acct = accountRepository.findByPhone(identifier)
                .or(() -> accountRepository.findByAccountNo(account == null ? "" : account.trim()))
                .orElseGet(() -> {
                    // 不存在的账号同样计入失败，防止用登录接口探测注册情况
                    loginThrottleService.recordFailure(identifier, clientIp);
                    return null;
                });
        if (acct == null) {
            // 统一话术防用户枚举：不区分「账号不存在」与「密码错误」
            throw new IllegalArgumentException("账号或密码与记录不一致，请检查后重试");
        }
        if (acct.getRole() != role) {
            throw new IllegalArgumentException("该账号不属于当前所选角色门户，请切换角色后重试");
        }
        if (!acct.passwordMatches(password, passwordEncoder)) {
            loginThrottleService.recordFailure(identifier, clientIp);
            throw new IllegalArgumentException("账号或密码与记录不一致");
        }
        requireUsable(acct);
        loginThrottleService.recordSuccess(identifier);
        return issueSession(acct);
    }

    @Transactional
    public Session loginBySms(CallerRole role, String phone, String smsCode, boolean codePreVerified) {
        if (!codePreVerified) {
            throw new IllegalArgumentException("验证码校验未通过");
        }
        Account acct = accountRepository.findByPhone(phone)
                .orElseThrow(() -> new IllegalArgumentException("该手机号尚未注册"));
        if (acct.getRole() != role) {
            throw new IllegalArgumentException("该账号不属于当前所选角色门户，请切换角色后重试");
        }
        requireUsable(acct);
        return issueSession(acct);
    }

    /** 锁定/注销账户拒绝登录（运营违规干预的执行闭环）。 */
    private void requireUsable(Account acct) {
        if (acct.getStatus() != AccountStatus.ACTIVE) {
            throw new IllegalStateException("该账户当前不可登录（状态: " + acct.getStatus() + "），请联系运营处理");
        }
    }

    /**
     * 刷新令牌轮换（Rotation）：旧令牌单次使用，换发新对后立即吊销；
     * 已吊销/过期令牌再次出现视为泄露信号，吊销该账户全部刷新令牌。
     * 锁定/注销账户不得续期会话：续期前 requireUsable。
     */
    @Transactional
    public Session refresh(String refreshToken) {
        String hash = sha256(refreshToken);
        RefreshToken token = refreshTokenRepository.findByTokenHash(hash).orElse(null);
        if (token == null) {
            throw new IllegalArgumentException("刷新凭证无效或已过期，请重新登录");
        }
        if (!token.usable(Instant.now())) {
            // 重用检测：泄露的旧令牌被再次使用 → 全端吊销，强制重新登录
            refreshTokenRepository.findByAccountId(token.getAccountId())
                    .forEach(RefreshToken::revoke);
            throw new IllegalArgumentException("检测到刷新凭证重用，为保护账户已退出全部会话，请重新登录");
        }
        Account acct = accountRepository.findById(token.getAccountId())
                .orElseThrow(() -> new IllegalStateException("账号不存在"));
        requireUsable(acct);
        Session session = issueSession(acct);
        token.revoke(); // 单次使用轮换：新令牌已在 issueSession 落库
        return session;
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokenRepository.findByTokenHash(sha256(refreshToken)).ifPresent(RefreshToken::revoke);
    }

    public Session issueSession(Account acct) {
        Set<Scope> scopes = JwtService.defaultScopesFor(acct.getRole());
        String access = jwtService.issueAccessToken(acct.getId(), acct.getRole(), acct.getDisplayName(), scopes);
        String refresh = newRefreshToken(acct.getId());
        return new Session(access, refresh, acct.getAccountNo(), acct.getDisplayName(),
                acct.getRole(), Instant.now().plus(securityProperties.jwt().accessTtl()).toEpochMilli());
    }

    private String newRefreshToken(UUID accountId) {
        String raw = UUID.randomUUID() + "-" + RANDOMToken();
        refreshTokenRepository.save(new RefreshToken(accountId, sha256(raw), Instant.now()
                .plus(securityProperties.jwt().refreshTtl())));
        return raw;
    }

    // ---------------------------------------------- 供其他模块调用的查询（仅 app 层出口）

    /** 账户显示名（不存在返回空）。 */
    @Transactional(readOnly = true)
    public java.util.Optional<String> displayNameOf(UUID accountId) {
        return accountRepository.findById(accountId).map(Account::getDisplayName);
    }

    @Transactional(readOnly = true)
    public boolean existsAccount(UUID accountId) {
        return accountRepository.existsById(accountId);
    }

    @Transactional(readOnly = true)
    public long countByRole(CallerRole role) {
        return accountRepository.findByRole(role).size();
    }

    /** 运营治理视图。 */
    public record AccountAdminView(UUID accountId, CallerRole role, String displayName,
                                   String phone, String accountNo, String status) {
    }

    private AccountAdminView toAdminView(Account a) {
        return new AccountAdminView(a.getId(), a.getRole(), a.getDisplayName(),
                a.getPhone(), a.getAccountNo(), a.getStatus().name());
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<AccountAdminView> adminAccounts(org.springframework.data.domain.Pageable pageable) {
        return accountRepository.findAll(pageable).map(this::toAdminView);
    }

    @Transactional(readOnly = true)
    public java.util.Optional<AccountAdminView> adminAccountById(UUID accountId) {
        return accountRepository.findById(accountId).map(this::toAdminView);
    }

    /** 锁定/解锁状态变更（治理动作本身与审计由调用方运营模块完成）。 */
    @Transactional
    public AccountAdminView setLocked(UUID accountId, boolean locked) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("账户不存在"));
        if (locked) {
            account.lock();
        } else {
            account.unlock();
        }
        return toAdminView(account);
    }

    /** 运营引导：创建初始管理员（幂等）。 */
    @Transactional
    public AccountAdminView createAdminAccount(String phone, String displayName, String rawPassword) {
        Account account = accountRepository.findByPhone(phone).orElseGet(() ->
                // P1 修复：编号必须由生成器分配——原硬编码 NUR-ADM-000001 使第二个运营账号
                // 必然违反 ux_accounts_account_no 唯一约束而无法创建
                accountRepository.save(new Account(CallerRole.ADMIN, nextAccountNo(CallerRole.ADMIN), phone,
                        displayName, passwordEncoder.encode(rawPassword))));
        return toAdminView(account);
    }

    // ---------------------------------------------- 统一认证层账户状态闸门

    /** 账户是否可用：每个请求直查数据库，零缓存窗口（D3）。 */
    @Override
    @Transactional(readOnly = true)
    public boolean active(UUID accountId) {
        return accountRepository.findById(accountId)
                .map(a -> a.getStatus() == AccountStatus.ACTIVE)
                .orElse(false);
    }

    // ------------------------------------------------------- Agent API Token

    /** 生成 Agent API Token：明文仅返回一次，库中只存定位哈希（唯一索引）+ 加盐哈希。 */
    @Transactional
    public IssuedToken issueApiToken(UUID operatorId, String name, List<String> scopeCodes, long validDays) {
        Set<Scope> scopes = scopeCodes.stream()
                .map(Scope::fromCode)
                .filter(s -> s != null)
                .collect(Collectors.toUnmodifiableSet());
        if (scopes.isEmpty()) {
            throw new IllegalArgumentException("至少声明一个有效 Scope（最小权限原则）");
        }
        String salt = RANDOMToken().substring(0, 32);
        String raw = securityProperties.apiToken().prefix() + UUID.randomUUID() + RANDOMToken();
        String lookupHash = sha256(raw);
        String hash = sha256(raw + salt);
        ApiToken token = new ApiToken(operatorId, name, lookupHash, hash, salt,
                scopes.stream().map(Scope::code).collect(Collectors.joining(",")),
                Instant.now().plus(Duration.ofDays(validDays)));
        apiTokenRepository.save(token);
        return new IssuedToken(raw, token.getId(), token.getExpiresAt());
    }

    @Transactional
    public void revokeApiToken(UUID tokenId) {
        ApiToken token = apiTokenRepository.findById(tokenId)
                .orElseThrow(() -> new IllegalArgumentException("API Token 不存在"));
        token.revoke();
    }

    /**
     * Agent Token 验证：按定位哈希（未加盐 SHA-256(raw)）直查唯一行，再做盐值哈希的
     * 时间安全二次确认——验证路径 O(1) 定位，杜绝全表遍历。
     */
    @Override
    @Transactional
    public Optional<Verification> verify(String rawToken) {
        return apiTokenRepository.findByLookupHash(sha256(rawToken))
                .filter(t -> t.usable(Instant.now()))
                .filter(t -> t.hashMatches(sha256(rawToken + t.getSalt())))
                // 所有者账户状态闸门：锁定/注销账户名下的 Agent Token 一并失效
                .filter(t -> active(t.getOwnerAccountId()))
                .map(t -> {
                    t.touchUsed();
                    Set<Scope> scopes = Arrays.stream(t.getScopes().split(","))
                            .map(Scope::fromCode).filter(s -> s != null)
                            .collect(Collectors.toUnmodifiableSet());
                    return new Verification(t.getOwnerAccountId(), "Agent:" + t.getName(), scopes);
                });
    }

    private String nextAccountNo(CallerRole role) {
        String prefix = switch (role) {
            case STUDENT -> "NUR-STU-";
            case PARENT -> "NUR-PAR-";
            case TEACHER -> "NUR-TEA-";
            case ADMIN -> "NUR-ADM-";
            case AGENT -> "NUR-AGT-";
        };
        return prefix + String.format("%06d", 1 + RANDOM.nextInt(999_999));
    }

    private String RANDOMToken() {
        byte[] bytes = new byte[16];
        new SecureRandom().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** 供 BootstrapAdminRunner 使用。 */
    public Account createAdminIfAbsent(String phone, String displayName, String rawPassword) {
        return accountRepository.findByPhone(phone).orElseGet(() ->
                accountRepository.save(new Account(CallerRole.ADMIN, nextAccountNo(CallerRole.ADMIN),
                        phone, displayName, passwordEncoder.encode(rawPassword))));
    }

    public record Session(String accessToken, String refreshToken, String accountNo,
                          String displayName, CallerRole role, long expiresAtEpochMs) {

        public CallerPrincipal toPrincipal(UUID accountId) {
            return new CallerPrincipal(accountId, role, displayName, JwtService.defaultScopesFor(role));
        }
    }

    public record IssuedToken(String plaintextOnce, UUID tokenId, Instant expiresAt) {
    }
}
