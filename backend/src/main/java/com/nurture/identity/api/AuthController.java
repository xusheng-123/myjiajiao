package com.nurture.identity.api;

import com.nurture.identity.app.IdentityApplicationService;
import com.nurture.identity.app.SmsCodeStore;
import com.nurture.shared.security.AuthCookies;
import com.nurture.shared.security.CallerRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 统一鉴权门户 API — 手机号/验证码、账号/密码双通道 + 注册/入驻申请。
 */
@RestController
@RequestMapping("/api/v1/identity/auth")
@Tag(name = "identity / 统一鉴权门户", description = "多角色登录、验证码、注册与入驻申请")
public class AuthController {

    private final IdentityApplicationService identityService;
    private final SmsCodeStore smsCodeStore;
    private final com.nurture.identity.app.TeacherRegistrationService teacherRegistration;

    public AuthController(IdentityApplicationService identityService, SmsCodeStore smsCodeStore,
                           com.nurture.identity.app.TeacherRegistrationService teacherRegistration) {
        this.identityService = identityService;
        this.smsCodeStore = smsCodeStore;
        this.teacherRegistration = teacherRegistration;
    }

    public record SmsCodeRequest(
            @jakarta.validation.constraints.NotNull @Parameter(description = "角色门户", required = true) CallerRole role,
            @NotBlank @Pattern(regexp = "1\\d{10}", message = "手机号需为 11 位数字") String phone) {
    }

    @org.springframework.web.bind.annotation.GetMapping("/capabilities")
    public java.util.Map<String, Object> capabilities() {
        return java.util.Map.of("smsAvailable", smsCodeStore.available(), "passwordAvailable", true);
    }

    public record LoginByPasswordRequest(CallerRole role, String account, String password) {
    }

    public record LoginBySmsRequest(CallerRole role,
                                    @Pattern(regexp = "1\\d{10}") String phone,
                                    @Pattern(regexp = "\\d{6}") String smsCode) {
    }

    public record RegisterStudentRequest(
            @NotBlank @Size(max = 64) String displayName,
            @Pattern(regexp = "1\\d{10}") String phone,
            @NotBlank @Size(min = 8, max = 64) String password) {
    }

    public record RegisterTeacherRequest(
            @NotBlank @Size(max = 64) String displayName,
            @Pattern(regexp = "1\\d{10}") String phone,
            @NotBlank @Size(min = 8, max = 64) String password,
            @NotBlank @Size(max = 128) String school,
            @NotBlank @Size(max = 32) String highestDegree,
            @jakarta.validation.constraints.Min(0) @jakarta.validation.constraints.Max(80) int yearsOfTeaching) {
    }

    public record AuthResponse(String accessToken, String refreshToken, String accountNo,
                               String displayName, CallerRole role, long expiresAtEpochMs) {
    }

    @PostMapping("/sms-codes")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "发送短信验证码", description = "5 分钟内有效；同号限频：60 秒最小间隔，"
            + "每小时/每日配额见 nurture.sms.rate-limit 配置，超限返回 429 + Retry-After")
    public void sendSmsCode(@Valid @RequestBody SmsCodeRequest request) {
        smsCodeStore.send(request.role().name(), request.phone());
    }

    @PostMapping("/login:password")
    @Operation(summary = "账号/密码登录", description = "返回 JWT 凭证；前端依据角色字段分流至对应控制台。"
            + "连续失败将触发临时锁定（防暴力破解），可联系运营解锁")
    public AuthResponse loginByPassword(@Valid @RequestBody LoginByPasswordRequest request,
                                        jakarta.servlet.http.HttpServletRequest httpRequest,
                                        jakarta.servlet.http.HttpServletResponse httpResponse) {
        IdentityApplicationService.Session session =
                identityService.loginByPassword(request.role(), request.account(), request.password(),
                        clientIp(httpRequest));
        AuthCookies.attach(httpResponse, httpRequest, session.accessToken(), session.refreshToken());
        return toResponse(session);
    }

    /** 客户端来源：优先 X-Forwarded-For 首跳（Cloudflare Tunnel 反代透传），回落 remoteAddr。 */
    private String clientIp(jakarta.servlet.http.HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    @PostMapping("/login:sms")
    @Operation(summary = "手机号/验证码登录")
    public AuthResponse loginBySms(@Valid @RequestBody LoginBySmsRequest request,
                                   jakarta.servlet.http.HttpServletRequest httpRequest,
                                   jakarta.servlet.http.HttpServletResponse httpResponse) {
        boolean verified = smsCodeStore.verify(request.role().name(), request.phone(), request.smsCode());
        IdentityApplicationService.Session session =
                identityService.loginBySms(request.role(), request.phone(), request.smsCode(), verified);
        AuthCookies.attach(httpResponse, httpRequest, session.accessToken(), session.refreshToken());
        return toResponse(session);
    }

    @PostMapping("/register:student")
    @Operation(summary = "学生注册并登录")
    public AuthResponse registerStudent(@Valid @RequestBody RegisterStudentRequest request,
                                        jakarta.servlet.http.HttpServletRequest httpRequest,
                                        jakarta.servlet.http.HttpServletResponse httpResponse) {
        var account = identityService.register(CallerRole.STUDENT, request.displayName(),
                request.phone(), request.password(), clientIp(httpRequest));
        var session = identityService.issueSession(account);
        AuthCookies.attach(httpResponse, httpRequest, session.accessToken(), session.refreshToken());
        return toResponse(session);
    }

    @PostMapping("/register:parent")
    @Operation(summary = "家长注册并登录", description = "家长为独立实体账户，注册后可绑定子女成为监护人")
    public AuthResponse registerParent(@Valid @RequestBody RegisterStudentRequest request,
                                      jakarta.servlet.http.HttpServletRequest httpRequest,
                                      jakarta.servlet.http.HttpServletResponse httpResponse) {
        var account = identityService.register(CallerRole.PARENT, request.displayName(),
                request.phone(), request.password(), clientIp(httpRequest));
        var session = identityService.issueSession(account);
        AuthCookies.attach(httpResponse, httpRequest, session.accessToken(), session.refreshToken());
        return toResponse(session);
    }

    @PostMapping("/register:teacher")
    @Operation(summary = "导师入驻申请并登录", description = "注册后进入资质认证审核流程（verification 域）")
    public AuthResponse registerTeacher(@Valid @RequestBody RegisterTeacherRequest request,
                                        jakarta.servlet.http.HttpServletRequest httpRequest,
                                        jakarta.servlet.http.HttpServletResponse httpResponse) {
        var session = teacherRegistration.register(request.displayName(), request.phone(), request.password(),
                request.school(), request.highestDegree(), request.yearsOfTeaching(), clientIp(httpRequest));
        AuthCookies.attach(httpResponse, httpRequest, session.accessToken(), session.refreshToken());
        return toResponse(session);
    }

    public record RefreshRequest(String refreshToken) {
    }

    @PostMapping("/refresh")
    @Operation(summary = "刷新访问凭证", description = "携带未过期的 refreshToken 换发新凭证对")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest request,
                                jakarta.servlet.http.HttpServletResponse httpResponse,
                                jakarta.servlet.http.HttpServletRequest httpRequest) {
        String refreshToken = request.refreshToken() == null || request.refreshToken().isBlank()
                ? AuthCookies.refreshFrom(httpRequest) : request.refreshToken();
        var session = identityService.refresh(refreshToken);
        AuthCookies.attach(httpResponse, httpRequest, session.accessToken(), session.refreshToken());
        return toResponse(session);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "退出登录", description = "吊销 refreshToken；JWT 依赖短时效自然失效")
    public void logout(@Valid @RequestBody RefreshRequest request,
                           jakarta.servlet.http.HttpServletRequest httpRequest,
                           jakarta.servlet.http.HttpServletResponse httpResponse) {
        String refreshToken = request.refreshToken() == null || request.refreshToken().isBlank()
                ? AuthCookies.refreshFrom(httpRequest) : request.refreshToken();
        if (refreshToken != null && !refreshToken.isBlank()) {
            identityService.logout(refreshToken);
        }
        AuthCookies.clear(httpResponse, httpRequest);
    }

    private AuthResponse toResponse(IdentityApplicationService.Session session) {
        return new AuthResponse(session.accessToken(), session.refreshToken(), session.accountNo(),
                session.displayName(), session.role(), session.expiresAtEpochMs());
    }
}
