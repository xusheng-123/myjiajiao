package com.nurture.admin.api;

import com.nurture.admin.app.AdminApplicationService;
import com.nurture.identity.app.LoginThrottleService;
import com.nurture.refund.app.RefundApplicationService;

import com.nurture.shared.audit.AuditLog;
import com.nurture.shared.security.CallerContext;
import com.nurture.verification.app.VerificationApplicationService;
import com.nurture.payment.app.PaymentApplicationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * 运营治理控制台 API — 全景概览、审核状态机、退款仲裁（两阶段）与审计流水检视。
 */
@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "admin / 运营治理", description = "运营概览、资质审核流转、差错退款仲裁、不可篡改审计日志")
@SecurityRequirement(name = "bearerJWT")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {
    private static PageRequest page(int page,int size,String field) {
        if(page<0 || page>10000 || size<1 || size>100)throw new IllegalArgumentException("分页参数超出范围");
        return PageRequest.of(page,size,org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC,field,"id"));
    }

    private final AdminApplicationService adminService;
    private final VerificationApplicationService verificationService;
    private final RefundApplicationService refundService;
    private final LoginThrottleService loginThrottleService;
    private final PaymentApplicationService paymentService;
    

    public AdminController(AdminApplicationService adminService,
                           VerificationApplicationService verificationService,
                           RefundApplicationService refundService,
                           LoginThrottleService loginThrottleService,
                           PaymentApplicationService paymentService) {
        this.adminService = adminService;
        this.verificationService = verificationService;
        this.refundService = refundService;
        this.loginThrottleService = loginThrottleService;
        this.paymentService = paymentService;
    }

    public record DecideVerificationRequest(boolean approve, String note) {
    }

    public record ArbitrationExecuteRequest(boolean approve, @jakarta.validation.constraints.Size(max = 512) String decisionNote, String confirmationToken) {
    }

    public record UpdateConfigRequest(String value) {
    }

    // ------------------------------------------------------------ 支付运营

    @PostMapping("/orders/{orderId}:confirm-offline-payment")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "运营线下收款确认（支付模式 B）",
            description = "运营核验银行转账/收款码到账后将待支付订单标记为已付；资金不经平台网关，凭运营核验落账；幂等")
    public Map<String, Object> confirmOfflinePayment(@PathVariable java.util.UUID orderId) {
        return paymentService.confirmOfflinePayment(orderId);
    }

    // ------------------------------------------------------------ 概览与审计

    @GetMapping("/overview")
    @Operation(summary = "平台全景运营数据概览")
    public Map<String, Object> overview() {
        return adminService.overview();
    }

    @GetMapping("/audit-logs")
    @Operation(summary = "不可篡改审计日志流水检视", description = "全量记录调用者身份、时间戳、URI、动作、载荷哈希与状态")
    public Page<com.nurture.admin.app.AdminApplicationService.AuditLogView> auditLogs(
            @RequestParam(required = false) String actionType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return adminService.auditLogViews(actionType, page(page,size,"occurredAt"));
    }

    // ------------------------------------------------------------ 资质审核

    @GetMapping("/verification-cases")
    @Operation(summary = "教师资质资料审核流转列表", description = "status 可选：SUBMITTED / UNDER_REVIEW / APPROVED / REJECTED")
    public Page<com.nurture.verification.app.VerificationApplicationService.VerificationCaseView> verificationCases(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return verificationService.listViewByStatus(status, page(page,size,"createdAt"));
    }

    @PostMapping("/verification-cases/{caseId}:decide")
    @Operation(summary = "资质审核决定", description = "approve 进入通过状态；reject 必须附明确原因")
    public com.nurture.verification.app.VerificationApplicationService.VerificationCaseView decideVerification(
            @PathVariable UUID caseId, @RequestBody DecideVerificationRequest request) {
        return adminService.decideVerification(caseId, request.approve(), request.note());
    }

    @GetMapping("/verification-cases/{caseId}/materials/{kind}")
    @Operation(summary = "受控下载资质材料", description = "仅运营可访问，读取落审计，禁止缓存，不暴露存储键")
    public org.springframework.http.ResponseEntity<byte[]> verificationMaterial(@PathVariable UUID caseId, @PathVariable String kind) {
        var material = verificationService.material(caseId, kind);
        return org.springframework.http.ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM)
                .header("Content-Disposition", org.springframework.http.ContentDisposition.attachment().filename(material.filename()).build().toString())
                .header("Cache-Control", "no-store, private")
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "sandbox; default-src 'none'")
                .body(material.content());
    }

    // ------------------------------------------------------------ 退款仲裁

    @GetMapping("/refund-cases")
    @Operation(summary = "差错退款案例列表")
    public Page<com.nurture.refund.app.RefundApplicationService.RefundCaseView> refundCases(
            @RequestParam(required=false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return refundService.listAllViews(status,page(page,size,"createdAt"));
    }

    @PostMapping("/refund-cases/{caseId}/arbitration:preview")
    @Operation(summary = "退款仲裁 · Preview",
            description = "两阶段第一阶段：预计算退款金额、影响课时、钱包入账与订单影响，返回短效 Confirmation Token")
    public RefundApplicationService.ArbitrationPreview arbitrationPreview(@PathVariable UUID caseId) {
        return refundService.previewArbitration(caseId);
    }

    @PostMapping("/refund-cases/{caseId}/arbitration:execute")
    @Operation(summary = "退款仲裁 · Execute",
            description = "两阶段第二阶段：携带 Confirmation Token 原子执行；全程落不可篡改审计")
    public com.nurture.refund.app.RefundApplicationService.RefundCaseView arbitrationExecute(
            @PathVariable UUID caseId, @RequestBody ArbitrationExecuteRequest request) {
        return refundService.executeArbitrationView(caseId, request.approve(), request.decisionNote(),
                request.confirmationToken());
    }

    // ------------------------------------------------------------ 账户管理

    public record LockAccountRequest(String note) {
    }

    @GetMapping("/accounts")
    @Operation(summary = "全站账户列表（内容与账户管理）")
    public Page<com.nurture.identity.app.IdentityApplicationService.AccountAdminView> accounts(
            @RequestParam(required=false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return adminService.listAccounts(status,page(page,size,"createdAt"));
    }

    @PostMapping("/accounts/{accountId}:lock")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "锁定违规账户", description = "违规干预动作，落不可篡改审计")
    public void lockAccount(@PathVariable UUID accountId, @RequestBody(required = false) LockAccountRequest request) {
        adminService.setAccountLocked(accountId, true, request == null ? "" : request.note());
    }

    @PostMapping("/accounts/{accountId}:unlock")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "解锁账户")
    public void unlockAccount(@PathVariable UUID accountId, @RequestBody(required = false) LockAccountRequest request) {
        adminService.setAccountLocked(accountId, false, request == null ? "" : request.note());
    }

    // ------------------------------------------------------------ 全局参数

    @PutMapping("/configs/{key}")
    @Operation(summary = "更新平台全局参数（如抽成比例）")
    public Map<String, String> updateConfig(@PathVariable String key, @RequestBody UpdateConfigRequest request) {
        var config = adminService.updateConfig(key, request.value(),
                CallerContext.require().accountId().toString());
        return Map.of("key", config.getKey(), "value", config.getValue());
    }

    // ------------------------------------------------------------ 安全治理

    public record ResetLoginThrottleRequest(String account, String ip) {
    }

    @PostMapping("/security/login-throttle:reset")
    @Operation(summary = "解锁登录限频（误锁申诉）", description = "清空指定账号的失败计数与临时锁定；"
            + "可附带 ip 同时解除该来源 IP 的登录限制。治理动作落审计")
    public Map<String, Object> resetLoginThrottle(@RequestBody ResetLoginThrottleRequest request) {
        String account = request.account() == null ? "" : request.account().trim().toLowerCase();
        String ip = request.ip() == null ? "" : request.ip().trim();
        if (account.isEmpty() && ip.isEmpty()) {
            throw new IllegalArgumentException("请至少提供 account 或 ip 之一");
        }
        if (!account.isEmpty()) {
            loginThrottleService.resetAccount(account);
        }
        if (!ip.isEmpty()) {
            loginThrottleService.resetIp(ip);
        }
        return Map.of("reset", true, "account", account, "ip", ip);
    }

    @GetMapping("/orders/pending-payment")
    @Operation(summary = "待支付订单列表（线下收款核对用）")
    public org.springframework.data.domain.Page<java.util.Map<String, Object>> pendingPaymentOrders(
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "0") int page,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "10") int size) {
        return adminService.pendingPaymentOrders(page(page,size,"createdAt"));
    }
}
