package com.nurture.teacher.api;

import com.nurture.booking.app.BookingApplicationService;
import com.nurture.teacher.app.TeacherApplicationService;
import com.nurture.teacher.domain.SubjectOffering;
import com.nurture.teacher.domain.TeacherProfile;
import com.nurture.shared.security.CallerContext;
import com.nurture.settlement.app.SettlementApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 导师工作台 API — 档案、擅长科目、课时定价与档期管理。
 */
@RestController
@PreAuthorize("hasRole('TEACHER') or hasRole('ADMIN') or hasAuthority('SCOPE_teacher:read') or hasAuthority('SCOPE_teacher:write')")
@RequestMapping("/api/v1/teacher/me")
@Tag(name = "teacher / 导师工作台", description = "资质档案、擅长科目、课时定价、教学风格")
@SecurityRequirement(name = "bearerJWT")
public class TeacherController {

    private final TeacherApplicationService teacherService;
    private final BookingApplicationService bookingService;
    private final SettlementApplicationService settlementService;

    public TeacherController(TeacherApplicationService teacherService,
                             BookingApplicationService bookingService,
                             SettlementApplicationService settlementService) {
        this.teacherService = teacherService;
        this.bookingService = bookingService;
        this.settlementService = settlementService;
    }

    public record UpdateProfileRequest(String school, String highestDegree, Integer yearsOfTeaching,
                                       String intro, Set<String> styleTags) {
    }

    public record AddSubjectRequest(String subject, String gradeBand, long pricePerHourCents) {
    }

    public record RepriceRequest(long pricePerHourCents) {
    }

    public record ProfileResponse(UUID teacherId, String teacherNo, String displayName, String school,
                                  String highestDegree, int yearsOfTeaching, String intro,
                                  Set<String> styleTags) {
    }

    public record SubjectOfferingResponse(UUID offeringId, String subject, String gradeBand,
                                          long pricePerHourCents, boolean active) {
        static SubjectOfferingResponse from(SubjectOffering o) {
            return new SubjectOfferingResponse(o.getId(), o.getSubject(), o.getGradeBand(),
                    o.getPricePerHourCents(), o.isActive());
        }
    }

    @GetMapping("/profile")
    @Operation(summary = "我的导师档案")
    public ProfileResponse profile() {
        var p = teacherService.profileViewOf(CallerContext.require().accountId());
        return new ProfileResponse(p.profileId(), p.teacherNo(), null, p.school(), p.highestDegree(),
                p.yearsOfTeaching(), p.intro(), p.styleTags());
    }

    @PutMapping("/profile")
    @Operation(summary = "更新导师档案", description = "院校、学历、教龄、个人介绍与教学风格标签")
    public ProfileResponse updateProfile(@RequestBody UpdateProfileRequest request) {
        var p = teacherService.updateProfileView(CallerContext.require().accountId(),
                request.school(), request.highestDegree(), request.yearsOfTeaching(),
                request.intro(), request.styleTags());
        return new ProfileResponse(p.profileId(), p.teacherNo(), null, p.school(), p.highestDegree(),
                p.yearsOfTeaching(), p.intro(), p.styleTags());
    }

    @GetMapping("/subjects")
    @Operation(summary = "我的开课条目（擅长科目与定价）")
    public List<SubjectOfferingResponse> subjects(@org.springframework.web.bind.annotation.RequestParam(defaultValue="false") boolean includeInactive) {
        return teacherService.offeringsOf(CallerContext.require().accountId(),includeInactive).stream()
                .map(SubjectOfferingResponse::from).toList();
    }

    @PostMapping("/subjects")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "新增开课条目", description = "科目 + 学段 + 课时定价（分/小时）")
    public SubjectOfferingResponse addSubject(@RequestBody AddSubjectRequest request) {
        return SubjectOfferingResponse.from(teacherService.addSubject(CallerContext.require().accountId(),
                request.subject(), request.gradeBand(), request.pricePerHourCents()));
    }

    @PutMapping("/subjects/{offeringId}/pricing")
    @Operation(summary = "调整课时定价")
    public SubjectOfferingResponse reprice(@PathVariable UUID offeringId, @RequestBody RepriceRequest request) {
        return SubjectOfferingResponse.from(teacherService.reprice(CallerContext.require().accountId(),
                offeringId, request.pricePerHourCents()));
    }

    @GetMapping("/booking-requests")
    @Operation(summary = "试听课与正式课预约申请审批面板", description = "返回发往本导师的预约申请，可逐条 approve/reject")
    public java.util.List<java.util.Map<String, Object>> bookingRequests(
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "0") int page,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "10") int size) {
        UUID me = CallerContext.require().accountId();
        return bookingService.requestSnapshots(me, org.springframework.data.domain.PageRequest.of(page, size))
                .getContent().stream()
                .map(b -> java.util.Map.<String, Object>of(
                        "bookingId", b.getId(),
                        "studentId", b.getStudentId(),
                        "subject", b.getSubject(),
                        "type", b.getType(),
                        "status", b.getStatus(),
                        "createdAt", b.getCreatedAt().toString()))
                .toList();
    }

    @PostMapping("/booking-requests/{bookingId}:decide")
    @Operation(summary = "审批预约申请", description = "decision=approve 确认并锁定时段；decision=reject 释放档期")
    public BookingApplicationService.BookingDecisionView decideBooking(@PathVariable UUID bookingId,
                                                       @org.springframework.web.bind.annotation.RequestParam String decision) {
        return "approve".equalsIgnoreCase(decision)
                ? bookingService.approveAsTeacher(bookingId)
                : bookingService.rejectAsTeacher(bookingId);
    }

    @GetMapping("/today-lessons")
    @Operation(summary = "我的当日课程", description = "今天已确认/进行中的课程安排，按开始时间排序")
    public java.util.List<BookingApplicationService.TodayLesson> todayLessons() {
        return bookingService.todayLessonsForTeacher(CallerContext.require().accountId());
    }

    @GetMapping("/settlement")
    @Operation(summary = "课酬结算流水明细", description = "待结算 / 已提现汇总与逐笔结算条目")
    public com.nurture.settlement.app.SettlementApplicationService.TeacherSettlement settlement() {
        return settlementService.summaryOf(CallerContext.require().accountId());
    }

    @DeleteMapping("/subjects/{offeringId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "下架开课条目")
    public void deactivate(@PathVariable UUID offeringId) {
        teacherService.deactivateSubject(CallerContext.require().accountId(), offeringId);
    }
    @PostMapping("/subjects/{offeringId}:activate")
    public SubjectOfferingResponse activate(@PathVariable UUID offeringId){return SubjectOfferingResponse.from(teacherService.activateSubject(CallerContext.require().accountId(),offeringId));}
}
