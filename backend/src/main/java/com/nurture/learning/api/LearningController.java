package com.nurture.learning.api;

import com.nurture.learning.app.LearningApplicationService;
import com.nurture.shared.security.CallerContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * 学习成长 API — 课后任务 / 课堂记录 / 学习档案 / 成长中心 / 家长周报 / 课后答疑。
 * 权限：学生仅本人数据；教师仅「有授课关系」的学生；家长仅 ACTIVE 绑定的孩子（周报内校验）。
 */
@RestController
@RequestMapping("/api/v1/learning")
@Tag(name = "learning / 学习成长", description = "课后任务 · 课堂记录 · 学习档案 · 周报 · 答疑")
@SecurityRequirement(name = "bearerJWT")
public class LearningController {

    private final LearningApplicationService learning;

    public LearningController(LearningApplicationService learning) {
        this.learning = learning;
    }

    // ---------------- 学生：成长中心 / 任务 / 档案 / 答疑 ----------------
    public record TaskSubmission(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=10000) String content) {}
    public record TaskFeedback(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=4000) String feedback) {}
    public record QuestionAnswer(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=4000) String answer) {}

    @GetMapping("/growth")
    @PreAuthorize("hasRole('STUDENT') or hasRole('ADMIN')")
    @Operation(summary = "学习成长中心（今日/本周/最近动态）")
    public Map<String, Object> growth() {
        return learning.growthOf(CallerContext.require().accountId());
    }

    @GetMapping("/tasks")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "我的课后任务")
    public Object tasks(@RequestParam(required = false) String status) {
        return learning.tasksOfStudent(CallerContext.require().accountId(), status);
    }

    @PostMapping("/tasks/{taskId}:submit")
    @PreAuthorize("hasRole('STUDENT')")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "提交课后任务（幂等：重复提交被拒绝）")
    public Map<String, Object> submitTask(@PathVariable UUID taskId,
                                          @jakarta.validation.Valid @RequestBody TaskSubmission body) {
        return learning.submitTask(CallerContext.require().accountId(), taskId,
                body.content());
    }

    @GetMapping("/archive")
    @PreAuthorize("hasRole('STUDENT') or hasRole('ADMIN')")
    @Operation(summary = "学习档案（画像 + 统计 + 课堂记录 + 教学历史）")
    public Map<String, Object> archive() {
        return learning.archiveOf(CallerContext.require().accountId());
    }

    @GetMapping("/profile")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "学习目标 / 阶段 / 目标科目")
    public Object profile() {
        return learning.ensureProfile(CallerContext.require().accountId());
    }

    @PutMapping("/profile")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "更新学习画像")
    public Object updateProfile(@RequestBody Map<String, String> body) {
        return learning.updateProfile(CallerContext.require().accountId(),
                body.get("goal"), body.get("stage"), body.get("targetSubjects"));
    }

    // ---------------- 教师：任务 / 课堂记录 / 学生档案 / 答疑 ----------------

    @PostMapping("/teacher/tasks")
    @PreAuthorize("hasRole('TEACHER') or hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "发布课后任务（教师须为该课程归属教师）")
    public Map<String, Object> createTask(@RequestBody Map<String, String> body) {
        UUID teacherId = CallerContext.require().accountId();
        Instant deadline = body.get("deadline") == null || body.get("deadline").isBlank()
                ? null : Instant.parse(body.get("deadline"));
        return learning.createTask(teacherId, UUID.fromString(body.get("bookingId")),
                body.get("title"), body.get("description"), body.get("priority"), deadline);
    }

    @GetMapping("/teacher/tasks")
    @PreAuthorize("hasRole('TEACHER')")
    @Operation(summary = "我布置的课后任务")
    public Object teacherTasks() {
        return learning.tasksOfTeacher(CallerContext.require().accountId());
    }

    @PostMapping("/tasks/{taskId}:feedback")
    @PreAuthorize("hasRole('TEACHER') or hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "任务反馈（提交态 → 完成）")
    public Map<String, Object> feedbackTask(@PathVariable UUID taskId,
                                            @jakarta.validation.Valid @RequestBody TaskFeedback body) {
        return learning.feedbackTask(CallerContext.require().accountId(), taskId,
                body.feedback());
    }

    @PostMapping("/teacher/lesson-records")
    @PreAuthorize("hasRole('TEACHER') or hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "创建课堂记录（每节课一条，教师须为该课程归属教师）")
    public Map<String, Object> createRecord(@RequestBody Map<String, String> body) {
        return learning.createRecord(CallerContext.require().accountId(),
                UUID.fromString(body.get("bookingId")), body.get("subject"),
                body.get("knowledgePoints"), body.get("performance"),
                body.get("problems"), body.get("nextSuggestion"), body.get("note"));
    }

    @GetMapping("/teacher/lesson-records")
    @PreAuthorize("hasRole('TEACHER')")
    @Operation(summary = "我的课堂记录")
    public Object myRecords() {
        return learning.recordsOfTeacher(CallerContext.require().accountId());
    }

    @GetMapping("/teacher/students")
    @PreAuthorize("hasRole('TEACHER')")
    @Operation(summary = "我的学生（来自历史预约，去重）")
    public Object myStudents() {
        return learning.studentsOfTeacher(CallerContext.require().accountId());
    }

    @GetMapping("/teacher/students/{studentId}")
    @PreAuthorize("hasRole('TEACHER')")
    @Operation(summary = "授权学生的学习档案（须有授课关系）")
    public Map<String, Object> studentArchive(@PathVariable UUID studentId) {
        learning.requireTeachingRelation(CallerContext.require().accountId(), studentId);
        return learning.archiveOf(studentId);
    }

    @GetMapping("/teacher/questions")
    @PreAuthorize("hasRole('TEACHER')")
    @Operation(summary = "收到的课后答疑")
    public Object incomingQuestions(@RequestParam(required = false) String status) {
        return learning.questionsOfTeacher(CallerContext.require().accountId(), status);
    }

    @PostMapping("/questions/{questionId}:answer")
    @PreAuthorize("hasRole('TEACHER')")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "回答课后答疑")
    public Map<String, Object> answer(@PathVariable UUID questionId,
                                      @jakarta.validation.Valid @RequestBody QuestionAnswer body) {
        return learning.answerQuestion(CallerContext.require().accountId(), questionId,
                body.answer());
    }

    // ---------------- 答疑共享 ----------------

    @PostMapping("/questions")
    @PreAuthorize("hasRole('STUDENT')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "向自己的教师提问（须有授课关系）")
    public Map<String, Object> ask(@RequestBody Map<String, String> body) {
        return learning.askQuestion(CallerContext.require().accountId(),
                UUID.fromString(body.get("teacherId")), body.getOrDefault("subject", "综合"),
                body.get("title"), body.get("content"));
    }

    @GetMapping("/questions")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "我的课后答疑")
    public Object myQuestions() {
        return learning.questionsOfStudent(CallerContext.require().accountId());
    }

    @PostMapping("/questions/{questionId}:close")
    @PreAuthorize("hasRole('STUDENT') or hasRole('TEACHER')")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "关闭答疑（仅参与双方）")
    public Map<String, Object> close(@PathVariable UUID questionId) {
        return learning.closeQuestion(CallerContext.require().accountId(), questionId);
    }
}
