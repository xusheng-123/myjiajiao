package com.nurture.verification.api;

import com.nurture.verification.app.VerificationApplicationService;
import com.nurture.verification.domain.VerificationCase;
import com.nurture.shared.security.CallerContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 资质合规 API — 导师侧提交与状态查询；运营侧审核在 admin 控制器。
 */
@RestController
@PreAuthorize("hasRole('TEACHER') or hasRole('ADMIN') or hasAuthority('SCOPE_teacher:write')")
@RequestMapping("/api/v1/verification")
@Tag(name = "verification / 资质合规", description = "教师身份与学历材料、审核状态机")
@SecurityRequirement(name = "bearerJWT")
public class VerificationController {

    private final VerificationApplicationService verificationService;

    public VerificationController(VerificationApplicationService verificationService) {
        this.verificationService = verificationService;
    }

    public record VerificationStatusView(String status, String decisionNote, String submittedAt,
                                         String decidedAt, String guidance) {
    }

    @PostMapping(value = "/cases", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "提交资质认证材料", description = "身份证明 + 学历证明；进入审核状态机")
    public VerificationStatusView submit(@RequestPart("idDocument") MultipartFile idDocument,
                                         @RequestPart("degreeCert") MultipartFile degreeCert) {
        var verificationCase = verificationService.submit(CallerContext.require().accountId(),
                idDocument, degreeCert);
        return toView(verificationCase);
    }

    @GetMapping("/me/status")
    @Operation(summary = "我的资质认证审核状态流")
    public VerificationStatusView myStatus() {
        VerificationCase verificationCase = verificationService.latestOfTeacher(CallerContext.require().accountId());
        if (verificationCase == null) {
            return new VerificationStatusView("NOT_SUBMITTED", null, null, null,
                    "尚未提交材料。请准备身份证明与学历证明后提交入驻审核。");
        }
        return toView(verificationCase);
    }

    private VerificationStatusView toView(VerificationCase c) {
        String guidance = switch (c.getStatus()) {
            case SUBMITTED -> "材料已收到，等待运营进入审核。";
            case UNDER_REVIEW -> "审核进行中，通常 1~2 个工作日出结果。";
            case APPROVED -> "已通过认证，可在工作台完善档期与定价。";
            case REJECTED -> "未通过：" + c.getDecisionNote() + "。补充材料后可重新提交，随时重新起步。";
            case null -> "";
        };
        return new VerificationStatusView(c.getStatus().name(), c.getDecisionNote(),
                c.getSubmittedAt() == null ? null : c.getSubmittedAt().toString(),
                c.getDecidedAt() == null ? null : c.getDecidedAt().toString(), guidance);
    }
}
