package com.nurture.admin.app;

import com.nurture.admin.domain.PlatformConfig;
import com.nurture.admin.infra.PlatformConfigRepository;
import com.nurture.shared.audit.AuditLogRepository;
import com.nurture.shared.audit.AuditLog;
import com.nurture.shared.audit.AuditService;
import com.nurture.booking.app.BookingApplicationService;
import com.nurture.identity.app.IdentityApplicationService;
import com.nurture.order.app.OrderApplicationService;
import com.nurture.shared.security.CallerRole;
import com.nurture.teacher.app.TeacherApplicationService;
import com.nurture.verification.app.VerificationApplicationService;
import com.nurture.verification.domain.VerificationCase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.Map;

/**
 * 运营治理应用服务 — 跨模块聚合查询（经各模块 app 服务，不直取仓储）。
 */
@Service
public class AdminApplicationService implements com.nurture.shared.config.PlatformConfigReader {

    private final PlatformConfigRepository configRepository;
    private final AuditLogRepository auditLogRepository;
    private final AuditService auditService;
    private final VerificationApplicationService verificationService;
    private final IdentityApplicationService identityService;
    private final BookingApplicationService bookingService;
    private final OrderApplicationService orderService;
    private final com.nurture.order.infra.OrderRepository orderRepository;
    private final TeacherApplicationService teacherService;

    public AdminApplicationService(PlatformConfigRepository configRepository,
                                   AuditLogRepository auditLogRepository,
                                   AuditService auditService,
                                   VerificationApplicationService verificationService,
                                   IdentityApplicationService identityService,
                                   BookingApplicationService bookingService,
                                   OrderApplicationService orderService,
                                   com.nurture.order.infra.OrderRepository orderRepository,
                                   TeacherApplicationService teacherService) {
        this.configRepository = configRepository;
        this.auditLogRepository = auditLogRepository;
        this.auditService = auditService;
        this.verificationService = verificationService;
        this.identityService = identityService;
        this.bookingService = bookingService;
        this.orderService = orderService;
        this.orderRepository = orderRepository;
        this.teacherService = teacherService;
    }

    // ------------------------------------------------------------ 平台参数

    @Override
    public java.util.Optional<String> get(String key) {
        return configRepository.findById(key).map(PlatformConfig::getValue);
    }

    @Transactional
    public PlatformConfig updateConfig(String key, String value, String operator) {
        PlatformConfig config = configRepository.findById(key)
                .orElseThrow(() -> new IllegalArgumentException("配置项不存在: " + key));
        config.update(value, operator);
        return config;
    }

    // ------------------------------------------------------------ 全景概览

    @Transactional(readOnly = true)
    public Map<String, Object> overview() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("studentAccounts", identityService.countByRole(CallerRole.STUDENT));
        data.put("parentAccounts", identityService.countByRole(CallerRole.PARENT));
        data.put("teacherAccounts", teacherService.countProfiles());
        data.put("activeBookings", bookingService.countAll());
        data.put("orders", orderService.countOrders());
        data.put("pendingVerifications", verificationService
                .listView(VerificationCase.VerificationStatus.SUBMITTED, Pageable.ofSize(1)).getTotalElements());
        data.put("platformFeeRate", get("settlement.platform-fee-rate").orElse("0.15"));
        return data;
    }

    // ------------------------------------------------------------ 资质审核

    @Transactional
    public VerificationApplicationService.VerificationCaseView decideVerification(UUID caseId, boolean approve, String note) {
        return verificationService.decide(caseId, approve, note, com.nurture.shared.security.CallerContext.require().accountId());
    }

    // ------------------------------------------------------------ 账户管理

    @Transactional(readOnly = true)
    public Page<IdentityApplicationService.AccountAdminView> listAccounts(Pageable pageable) {
        return identityService.adminAccounts(pageable);
    }

    /** 违规干预：锁定/解锁账户（PRD 1.2 内容与账户管理）。 */
    @Transactional
    public IdentityApplicationService.AccountAdminView setAccountLocked(UUID accountId, boolean locked, String note) {
        var view = identityService.adminAccountById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("账户不存在"));
        if (view.role() == CallerRole.ADMIN) {
            throw new IllegalStateException("运营管理员账户不允许在此锁定，请走内部流程");
        }
        identityService.setLocked(accountId, locked);
        auditService.record(locked ? "admin:account-lock" : "admin:account-unlock", "SUCCESS",
                Map.of("accountId", accountId.toString(), "note", note == null ? "" : note),
                "运营账户管理操作");
        return identityService.adminAccountById(accountId).orElse(null);
    }

    // ------------------------------------------------------------ 审计检视

    @Transactional(readOnly = true)
    public Page<AuditLogView> auditLogViews(String actionType, Pageable pageable) {
        return auditLogs(actionType, pageable);
    }

    @Transactional(readOnly = true)
    public Page<AuditLogView> auditLogs(String actionType, Pageable pageable) {
        Page<AuditLog> page = actionType == null || actionType.isBlank()
                ? auditLogRepository.findAll(pageable)
                : auditLogRepository.findByActionType(actionType, pageable);
        return page.map(a -> new AuditLogView(a.getId(), a.getCallerId(), a.getCallerRole(),
                a.getHttpMethod(), a.getRequestUri(), a.getActionType(), a.getPayloadHash(),
                a.getExecutionStatus(), a.getDetail(), a.getOccurredAt()));
    }

    public record AuditLogView(java.util.UUID auditId, java.util.UUID callerId, String callerRole,
                               String httpMethod, String requestUri, String actionType, String payloadHash,
                               String executionStatus, String detail, java.time.Instant occurredAt) {
    }

    // ------------------------------------------------- 引导创建初始运营账号

    @Transactional
    public IdentityApplicationService.AccountAdminView bootstrapAdmin(
            String phone, String displayName, String rawPassword, org.springframework.security.crypto.password.PasswordEncoder encoder) {
        return identityService.createAdminAccount(phone, displayName, rawPassword);
    }

    /** 运营收款台：待支付订单（含学生/教师名与课程，供线下收款确认前的核对）。 */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<Map<String, Object>> pendingPaymentOrders(
            org.springframework.data.domain.Pageable pageable) {
        var page = orderRepository.findByStatus(
                com.nurture.order.domain.Order.OrderStatus.PENDING_PAYMENT, pageable);
        return page.map(o -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("orderId", o.getId().toString());
            m.put("orderNo", o.getOrderNo());
            m.put("amountCents", o.getAmountCents());
            m.put("status", o.getStatus().name());
            m.put("createdAt", o.getCreatedAt().toString());
            m.put("payerName", identityService.displayNameOf(o.getPayerParentId()).orElse("家长"));
            m.put("teacherName", identityService.displayNameOf(o.getTeacherId()).orElse("导师"));
            m.put("studentName", identityService.displayNameOf(o.getStudentId()).orElse("学生"));
            m.put("subject", bookingService.get(o.getBookingId()).getSubject());
            return m;
        });
    }
}
