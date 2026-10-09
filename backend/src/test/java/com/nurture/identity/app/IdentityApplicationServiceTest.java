package com.nurture.identity.app;

import com.nurture.identity.domain.Account;
import com.nurture.identity.domain.AccountStatus;
import com.nurture.identity.domain.ApiToken;
import com.nurture.identity.domain.RefreshToken;
import com.nurture.identity.infra.AccountRepository;
import com.nurture.identity.infra.ApiTokenRepository;
import com.nurture.identity.infra.RefreshTokenRepository;
import com.nurture.shared.outbox.OutboxWriter;
import com.nurture.shared.security.CallerRole;
import com.nurture.shared.security.JwtService;
import com.nurture.shared.security.SecurityProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** D3/D4 回归：刷新令牌轮换与重用检测、锁定账户状态闸门。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IdentityApplicationServiceTest {

    @Mock AccountRepository accountRepository;
    @Mock ApiTokenRepository apiTokenRepository;
    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtService jwtService;
    @Mock OutboxWriter outboxWriter;
    @Mock LoginThrottleService loginThrottleService;
    @Mock RegistrationRateLimiter registrationRateLimiter;

    private IdentityApplicationService service;
    private final UUID accountId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        var properties = new SecurityProperties(
                new SecurityProperties.Jwt("test-secret-0123456789abcdef", "nurture", 30L, 14L),
                new SecurityProperties.ApiToken("nur_at_"),
                new SecurityProperties.BruteForce(5L, 15L, 15L, 100L));
        service = new IdentityApplicationService(accountRepository, apiTokenRepository,
                refreshTokenRepository, passwordEncoder, jwtService,
                properties, outboxWriter, loginThrottleService, registrationRateLimiter);
        // 供 issueSession 使用
        when(jwtService.issueAccessToken(any(), any(), anyString(), any())).thenReturn("access-token");
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private Account account(AccountStatus status) {
        Account account = mock(Account.class);
        lenient().when(account.getId()).thenReturn(accountId);
        lenient().when(account.getStatus()).thenReturn(status);
        lenient().when(account.getRole()).thenReturn(CallerRole.STUDENT);
        lenient().when(account.getDisplayName()).thenReturn("探针同学");
        lenient().when(account.getAccountNo()).thenReturn("NUR-STU-000001");
        return account;
    }

    private RefreshToken refreshToken(boolean usable) {
        RefreshToken token = mock(RefreshToken.class);
        lenient().when(token.getAccountId()).thenReturn(accountId);
        lenient().when(token.usable(any(Instant.class))).thenReturn(usable);
        return token;
    }

    @Test
    void 刷新成功即轮换_旧令牌立即吊销() {
        var old = refreshToken(true);
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(old));
        var account = account(AccountStatus.ACTIVE);
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

        var session = service.refresh("raw-refresh-token");

        assertThat(session.accessToken()).isEqualTo("access-token");
        assertThat(session.refreshToken()).isNotBlank();
        verify(old).revoke();                                  // 旧令牌单次使用后吊销
        verify(refreshTokenRepository).save(any(RefreshToken.class)); // 新令牌落库
        verify(refreshTokenRepository, never()).findByAccountId(any()); // 未触发重用全端吊销
    }

    @Test
    void 已吊销令牌被重用_吊销该账户全部会话() {
        var leaked = refreshToken(false);
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(leaked));
        var another = refreshToken(true);
        when(refreshTokenRepository.findByAccountId(accountId)).thenReturn(List.of(leaked, another));

        assertThatThrownBy(() -> service.refresh("raw-refresh-token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("重用");
        verify(leaked).revoke();
        verify(another).revoke();
        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
    }

    @Test
    void 未知刷新令牌直接拒绝() {
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.refresh("raw-refresh-token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("刷新凭证无效");
    }

    @Test
    void 锁定账户不得续期会话() {
        var token = refreshToken(true);
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));
        var locked = account(AccountStatus.LOCKED);
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(locked));

        assertThatThrownBy(() -> service.refresh("raw-refresh-token"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("不可登录");
        verify(token, never()).revoke();
    }

    @Test
    void 状态闸门_每次请求直查_锁定即时生效无缓存窗口() {
        var locked = account(AccountStatus.LOCKED);
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(locked));

        assertThat(service.active(accountId)).isFalse();
        assertThat(service.active(accountId)).isFalse();
        // 两次查询均触库：状态变化在下一请求立即生效（D3 零窗口）
        verify(accountRepository, org.mockito.Mockito.times(2)).findById(accountId);
    }

    @Test
    void 所有者被锁定的AgentToken失效() {
        var token = mock(ApiToken.class);
        lenient().when(token.getOwnerAccountId()).thenReturn(accountId);
        when(apiTokenRepository.findByLookupHash(anyString())).thenReturn(Optional.of(token));
        var locked = account(AccountStatus.LOCKED);
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(locked));

        assertThat(service.verify("nur_at_raw")).isEmpty();
    }
}
