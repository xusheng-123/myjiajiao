package com.nurture.student.api;

import com.nurture.booking.app.BookingApplicationService;
import com.nurture.classroom.app.ClassroomApplicationService;
import com.nurture.identity.app.IdentityApplicationService;
import com.nurture.shared.security.CallerContext;
import com.nurture.student.app.StudentApplicationService;
import com.nurture.student.domain.StudentProfile;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 学生控制台 API — 学情档案、专注模式、任务切块与提问入口。
 * 读端点为跨模块视图编排：booking.app（课程）+ identity.app（导师名）+ classroom.app（任务进度），
 * 学生域业务规则仍收敛在各模块 app 服务。
 */
@RestController
@PreAuthorize("hasRole('STUDENT') or hasRole('ADMIN') or hasAuthority('SCOPE_student:read') or hasAuthority('SCOPE_student:write')")
@RequestMapping("/api/v1/student/me")
@Tag(name = "student / 学生控制台", description = "学习偏好档案、迫近课程、课前任务切块、课后提问")
@SecurityRequirement(name = "bearerJWT")
public class StudentController {

    private static final String QUIET_EMPTY = "当前没有迫近课程。安静的休息同样宝贵，准备好时随时出发。";
    private static final String QUIET_ENCOURAGE = "按自己的节奏准备即可，完成几项都值得肯定。";

    private final StudentApplicationService studentService;
    private final ClassroomApplicationService classroomService;
    private final BookingApplicationService bookingService;
    private final IdentityApplicationService identityService;

    public StudentController(StudentApplicationService studentService,
                             ClassroomApplicationService classroomService,
                             BookingApplicationService bookingService,
                             IdentityApplicationService identityService) {
        this.studentService = studentService;
        this.classroomService = classroomService;
        this.bookingService = bookingService;
        this.identityService = identityService;
    }

    public record UpdatePreferenceRequest(String gradeLevel, String learningStyle, String focusPreference,
                                          Set<String> preferredSubjects) {
    }

    public record AskQuestionRequest(UUID teacherId, @NotBlank String question) {
    }

    public record StudentProfileResponse(UUID accountId, String studentNo, String gradeLevel,
                                         String learningStyle, String focusPreference,
                                         Set<String> preferredSubjects) {
    }

    public record TaskView(UUID taskId, String title, boolean completed) {
    }

    public record NextLesson(UUID bookingId, String subject, String teacherName, UUID teacherId,
                             String startAt, String status) {
    }

    public record FocusOverview(NextLesson nextLesson, List<TaskView> preparationTasks, String encouragement) {
    }

    public record UpcomingLessonView(UUID bookingId, String subject, String status,
                                     String startAt, UUID teacherId) {
    }

    @GetMapping("/profile")
    @Operation(summary = "专属学习偏好与学情档案")
    public StudentProfileResponse profile() {
        return toProfileView(studentService.ensureProfile(CallerContext.require().accountId()));
    }

    @PutMapping("/profile")
    @Operation(summary = "更新学习偏好", description = "学段、学习风格、专注偏好与擅长/薄弱科目")
    public StudentProfileResponse updatePreference(@RequestBody UpdatePreferenceRequest request) {
        var caller = CallerContext.require();
        var profile = studentService.updatePreference(caller.accountId(), request.gradeLevel(),
                request.learningStyle(), request.focusPreference(), request.preferredSubjects());
        return toProfileView(profile);
    }

    /** 迫近课程概览 — 专注模式数据源：booking.app 取课程、classroom.app 取任务、identity.app 取导师名。 */
    @GetMapping("/focus-overview")
    @Operation(summary = "专注模式概览", description = "仅返回迫近课程 + 课前任务进度，无图表无徽章，符合 ADHD 友好规范")
    public FocusOverview focusOverview() {
        UUID studentId = CallerContext.require().accountId();
        var upcoming = bookingService.upcomingForStudent(studentId).stream()
                .filter(b -> "CONFIRMED".equals(b.status()) || "IN_PROGRESS".equals(b.status()))
                .toList();
        if (upcoming.isEmpty()) {
            return new FocusOverview(null, List.of(), QUIET_EMPTY);
        }
        var next = upcoming.getFirst();
        String teacherName = identityService.displayNameOf(next.teacherId()).orElse("导师");
        List<TaskView> tasks = classroomService.taskViewsOf(next.bookingId()).stream()
                .map(t -> new TaskView(t.taskId(), t.title(), t.completed()))
                .toList();
        return new FocusOverview(new NextLesson(next.bookingId(), next.subject(), teacherName,
                next.teacherId(), next.startAt() == null ? null : next.startAt().toString(), next.status()),
                tasks, QUIET_ENCOURAGE);
    }

    /** 生成/获取迫近课程的课前任务切块（幂等：已存在则返回既有清单）。 */
    @PostMapping("/preparation-tasks")
    @Operation(summary = "生成/获取迫近课程的课前任务切块")
    public List<ClassroomApplicationService.TaskView> ensureTasks(java.util.Map<String, UUID> body) {
        return classroomService.preparationTaskViews(body.get("bookingId"));
    }

    /** 课后疑难提问：进入与导师的站内会话。 */
    @PostMapping("/questions")
    @Operation(summary = "课后疑难提问", description = "进入与导师的站内咨询会话")
    public void ask(@RequestBody AskQuestionRequest request) {
        studentService.askQuestion(request.teacherId(), request.question());
    }

    @GetMapping("/upcoming-lessons")
    @Operation(summary = "我的迫近课程")
    public List<UpcomingLessonView> upcomingLessons() {
        UUID studentId = CallerContext.require().accountId();
        return bookingService.upcomingForStudent(studentId).stream()
                .map(b -> new UpcomingLessonView(b.bookingId(), b.subject(), b.status(),
                        b.startAt() == null ? null : b.startAt().toString(), b.teacherId()))
                .toList();
    }

    @GetMapping("/lesson-history")
    @Operation(summary = "我的历史课程", description = "已完成/已结课课程，课程表「评价」入口的数据源")
    public List<UpcomingLessonView> lessonHistory() {
        UUID studentId = CallerContext.require().accountId();
        return bookingService.historyForStudent(studentId).stream()
                .map(b -> new UpcomingLessonView(b.bookingId(), b.subject(), b.status(),
                        b.startAt() == null ? null : b.startAt().toString(), b.teacherId()))
                .toList();
    }

    private static StudentProfileResponse toProfileView(StudentProfile profile) {
        return new StudentProfileResponse(profile.getAccountId(), profile.getStudentNo(),
                profile.getGradeLevel(), profile.getLearningStyle(), profile.getFocusPreference(),
                profile.getPreferredSubjects());
    }
}
