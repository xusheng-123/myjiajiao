package com.nurture.booking.api;

import com.nurture.booking.app.BookingApplicationService;
import com.nurture.booking.domain.Booking;
import com.nurture.shared.security.CallerContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 预约管理 API — 含高后果动作「取消预约」的两阶段执行接口。
 */
@RestController
@RequestMapping("/api/v1/booking")
@Tag(name = "booking / 预约管理", description = "试听与正式课预约、导师审批、两阶段取消")
@SecurityRequirement(name = "bearerJWT")
public class BookingController {

    private final BookingApplicationService bookingService;

    public BookingController(BookingApplicationService bookingService) {
        this.bookingService = bookingService;
    }

    public record CreateBookingRequest(@NotNull UUID teacherId, @NotNull UUID slotId,
                                       @NotBlank @Size(max=32) String subject, String bookingType) {
    }

    public record CancelPreviewRequest(@NotBlank String reason) {
    }

    public record CancelExecuteRequest(@NotBlank String reason, @NotBlank String confirmationToken) {
    }

    public record RescheduleRequest(@NotNull UUID newSlotId) {
    }

    public record BookingResponse(UUID bookingId, UUID studentId, UUID teacherId, UUID slotId,
                                  String subject, String type, String status, long priceCents,
                                  String cancelReason, java.time.Instant createdAt) {
        static BookingResponse from(Booking b) {
            return new BookingResponse(b.getId(), b.getStudentId(), b.getTeacherId(), b.getSlotId(),
                    b.getSubject(), b.getType().name(), b.getStatus().name(), b.getPriceCents(),
                    b.getCancelReason(), b.getCreatedAt());
        }
    }

    public record CancelPreviewResponse(java.util.Map<String, Object> cascadeImpact,
                                        String confirmationToken,
                                        java.time.Instant confirmationTokenExpiresAt,
                                        String notice) {
    }

@PreAuthorize("hasRole('STUDENT') or hasRole('PARENT') or hasRole('ADMIN') or hasAuthority('SCOPE_booking:create')")
    @PostMapping("/bookings")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "创建预约", description = "占用导师档期；试听课自动确认，正式课待导师审批")
    public BookingResponse create(@Valid @RequestBody CreateBookingRequest request) {
        if (request.bookingType() != null && !"FORMAL".equalsIgnoreCase(request.bookingType())
                && !"TRIAL".equalsIgnoreCase(request.bookingType())) {
            throw new IllegalArgumentException("bookingType仅支持TRIAL或FORMAL");
        }
        UUID studentId = CallerContext.require().accountId();
        Booking.BookingType type = "FORMAL".equalsIgnoreCase(request.bookingType())
                ? Booking.BookingType.FORMAL : Booking.BookingType.TRIAL;
        return BookingResponse.from(
                bookingService.create(studentId, request.teacherId(), request.slotId(), request.subject(), type));
    }

    @PostMapping("/bookings/{bookingId}:cancel/preview")
    @Operation(summary = "取消预约 · 第一阶段 Preview",
            description = "预计算级联影响（退款金额、影响课时、时段释放），返回短效 Confirmation Token")
    public CancelPreviewResponse cancelPreview(@PathVariable UUID bookingId,
                                               @Valid @RequestBody CancelPreviewRequest request) {
        var preview = bookingService.previewCancel(bookingId, request.reason());
        return new CancelPreviewResponse(preview.cascadeImpact(), preview.confirmationToken(),
                preview.confirmationTokenExpiresAt(),
                "请确认以上级联影响后，携带 confirmationToken 调用 execute 完成终态取消；凭证 10 分钟内有效。");
    }

    @PostMapping("/bookings/{bookingId}:cancel/execute")
    @Operation(summary = "取消预约 · 第二阶段 Execute",
            description = "携带 Confirmation Token 原子完成取消；已在 Idempotency-Key 防重保护下执行")
    public BookingResponse cancelExecute(@PathVariable UUID bookingId,
                                         @Valid @RequestBody CancelExecuteRequest request) {
        return BookingResponse.from(
                bookingService.executeCancel(bookingId, request.reason(), request.confirmationToken()));
    }

    /** 改期（D11）：参与双方可改期；档期冲突返回 409，可重新选择时间。 */
    @PostMapping("/bookings/{bookingId}:reschedule")
    @Operation(summary = "预约改期", description = "仅参与双方；新档期冲突时原子回滚，可重新选择时间；订单保持有效")
    public BookingResponse reschedule(@PathVariable UUID bookingId,
                                      @Valid @RequestBody RescheduleRequest request) {
        return BookingResponse.from(bookingService.reschedule(bookingId, request.newSlotId()));
    }

    @PostMapping("/booking-requests/{bookingId}:decide")
    @Operation(summary = "导师审批预约申请", description = "approve → 确认并锁定时段；reject → 释放档期")
    public BookingResponse decide(@PathVariable UUID bookingId, @RequestParam String decision) {
        if ("approve".equalsIgnoreCase(decision)) {
            var d = bookingService.approveAsTeacher(bookingId);
            return new BookingResponse(d.bookingId(), null, null, null, null, null, d.status(), 0, null, null);
        } else if ("reject".equalsIgnoreCase(decision)) {
            var d = bookingService.rejectAsTeacher(bookingId);
            return new BookingResponse(d.bookingId(), null, null, null, null, null, d.status(), 0, null, null);
        }
        throw new IllegalArgumentException("decision 仅支持 approve / reject");
    }

    @GetMapping("/me/bookings")
    @Operation(summary = "我的预约列表", description = "学生视角返回本人预约；教师请用 /teacher/me/booking-requests")
    public Page<BookingResponse> myBookings(@RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "10") int size) {
        UUID callerId = CallerContext.require().accountId();
        return bookingService.ofStudent(callerId, PageRequest.of(page, size)).map(BookingResponse::from);
    }

    @GetMapping("/trial-eligibility")
    public BookingApplicationService.TrialEligibility trialEligibility(@RequestParam UUID teacherId) {
        return bookingService.trialEligibility(teacherId);
    }

    @PostMapping("/bookings/{bookingId}:start")
    @Operation(summary = "开始上课", description = "状态机 REQUESTED→CONFIRMED→IN_PROGRESS→COMPLETED；参与双方可触发")
    public BookingResponse start(@PathVariable UUID bookingId) {
        return BookingResponse.from(bookingService.start(bookingId));
    }

    @PostMapping("/bookings/{bookingId}:complete")
    @Operation(summary = "完成课程", description = "完成后解锁双向评价；可进入结算与考勤")
    public BookingResponse complete(@PathVariable UUID bookingId) {
        return BookingResponse.from(bookingService.complete(bookingId));
    }

    @GetMapping("/bookings/{bookingId}")
    @Operation(summary = "预约详情")
    public BookingResponse get(@PathVariable UUID bookingId) {
        return BookingResponse.from(bookingService.get(bookingId));
    }
}
