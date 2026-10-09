package com.nurture.refund.api;

import com.nurture.refund.app.RefundApplicationService;
import com.nurture.refund.domain.RefundCase;
import com.nurture.shared.security.CallerContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 退款仲裁 API — 家长/学生侧发起申请；运营侧两阶段仲裁在 admin 控制器。
 */
@RestController
@RequestMapping("/api/v1/refund")
@Tag(name = "refund / 退款仲裁", description = "退款申请与两阶段影响评估")
@SecurityRequirement(name = "bearerJWT")
public class RefundController {

    private final RefundApplicationService refundService;

    public RefundController(RefundApplicationService refundService) {
        this.refundService = refundService;
    }

    public record RequestRefundRequest(@NotNull UUID orderId, @NotBlank String reason) {
    }

    public record RefundCaseView(UUID caseId, UUID orderId, String status, long computedAmountCents,
                                 int affectedLessons, String reason, String decisionNote) {
        static RefundCaseView from(RefundCase c) {
            return new RefundCaseView(c.getId(), c.getOrderId(), c.getStatus().name(),
                    c.getComputedAmountCents(), c.getAffectedLessons(), c.getReason(), c.getDecisionNote());
        }
    }

    @PostMapping("/cases")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "发起退款申请", description = "仅已支付订单可申请；仲裁执行由运营两阶段完成")
    public RefundCaseView request(@RequestBody RequestRefundRequest request) {
        return RefundCaseView.from(refundService.request(request.orderId(), request.reason()));
    }

    @GetMapping("/me/cases")
    @Operation(summary = "我发起的退款案例")
    public Page<RefundCaseView> myCases(@RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "10") int size) {
        return refundService.listByRequester(CallerContext.require().accountId(), PageRequest.of(page, size))
                .map(RefundCaseView::from);
    }
}
