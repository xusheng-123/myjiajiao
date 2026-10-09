package com.nurture.parent.api;

import com.nurture.parent.app.ParentApplicationService;
import com.nurture.shared.security.CallerContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * 家长控制台 API — 子女绑定、家庭支付钱包与对账明细。
 */
@RestController
@PreAuthorize("hasRole('PARENT') or hasRole('ADMIN') or hasAuthority('SCOPE_parent:read') or hasAuthority('SCOPE_parent:write')")
@RequestMapping("/api/v1/parent/me")
@Tag(name = "parent / 家长控制台", description = "绑定子女、家庭统一教育支付钱包、课时消费对账")
@SecurityRequirement(name = "bearerJWT")
public class ParentController {

    private final ParentApplicationService parentService;

    public ParentController(ParentApplicationService parentService) {
        this.parentService = parentService;
    }

    public record BindChildRequest(String studentNo, String relation) {
    }

    public record BindingResponse(UUID bindingId, UUID studentId, String studentNo, String status, String relation) {
    }

    public record TopUpRequest(@Positive long amountCents, String memo) {
    }

    /** 监护绑定确认（D1）：学生确认家长的绑定申请（方法级 STUDENT 覆盖类级 PARENT）。 */
    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping("/children:confirm")
    @Operation(summary = "学生确认监护绑定申请", description = "确认后绑定转为 ACTIVE，家长方可为该学生的预约下单")
    public BindingResponse confirmBinding() {
        var b = parentService.confirmBinding(CallerContext.require().accountId());
        return new BindingResponse(b.getId(), b.getStudentId(), null, b.getStatus().name(), b.getRelation());
    }

    /** 学生查看本人收到的待确认监护绑定申请（G3，与确认端点同一对象级语义）。 */
    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping("/children:pending")
    @Operation(summary = "我的待确认监护绑定申请",
            description = "仅返回当前学生本人收到的 PENDING 申请；确认后从列表消失并转为 ACTIVE")
    public List<ParentApplicationService.PendingBindingView> pendingBindings() {
        return parentService.pendingBindingsFor(CallerContext.require().accountId());
    }

    @PostMapping("/children")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "绑定子女", description = "依据学生编号发起监护绑定，学生侧确认后生效")
    public BindingResponse bindChild(@RequestBody BindChildRequest request) {
        var binding = parentService.bindChild(CallerContext.require().accountId(),
                request.studentNo(), request.relation());
        return new BindingResponse(binding.getId(), binding.getStudentId(), request.studentNo(),
                binding.getStatus().name(), binding.getRelation());
    }

    @GetMapping("/children")
    @Operation(summary = "已绑定子女列表")
    public java.util.List<BindingResponse> children() {
        return parentService.childrenOf(CallerContext.require().accountId()).stream()
                .map(b -> new BindingResponse(b.getId(), b.getStudentId(), null, b.getStatus().name(), b.getRelation()))
                .toList();
    }

    @GetMapping("/wallet")
    @Operation(summary = "家庭钱包概览与最近对账明细")
    public ParentApplicationService.WalletOverview wallet() {
        return parentService.walletOverview(CallerContext.require().accountId());
    }

    @PostMapping("/wallet/topup")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "家庭钱包充值", description = "首版经 Mock 支付渠道即时到账")
    public ParentApplicationService.WalletOverview topUp(@RequestBody TopUpRequest request) {
        parentService.topUp(CallerContext.require().accountId(), request.amountCents(), request.memo());
        return parentService.walletOverview(CallerContext.require().accountId());
    }
}
