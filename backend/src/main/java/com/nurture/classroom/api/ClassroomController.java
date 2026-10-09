package com.nurture.classroom.api;

import com.nurture.classroom.app.ClassroomApplicationService;
import com.nurture.classroom.domain.ClassroomSession;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 课堂协同 API — 会议凭证与入会前任务清单。
 */
@RestController
@RequestMapping("/api/v1/classroom")
@Tag(name = "classroom / 课堂协同", description = "外部会议凭证转发、入会前任务切块")
@SecurityRequirement(name = "bearerJWT")
public class ClassroomController {

    private final ClassroomApplicationService classroomService;

    public ClassroomController(ClassroomApplicationService classroomService) {
        this.classroomService = classroomService;
    }

    public record EnsureSessionRequest(String meetingUrl) {
    }

    public record MarkAttendanceRequest(String attendanceStatus) {
    }

    public record TaskView(UUID taskId, String title, int sortIndex, boolean completed) {
    }

    @PostMapping("/bookings/{bookingId}/session")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "创建课堂会话（导师）", description = "录入外部会议 SaaS 链接，形成课堂入口")
    public Map<String, String> ensureSession(@PathVariable UUID bookingId,
                                             @RequestBody EnsureSessionRequest request) {
        var session = classroomService.ensureSession(bookingId, request.meetingUrl());
        return Map.of("meetingUrl", session.getMeetingUrl(), "provider", session.getMeetingProvider());
    }

    @PostMapping("/bookings/{bookingId}/join")
    @Operation(summary = "进入课堂", description = "仅课程参与双方可获取入会凭证；返回高对比度主入口信息")
    public ClassroomApplicationService.JoinInfo join(@PathVariable UUID bookingId) {
        return classroomService.join(bookingId);
    }

    @PostMapping("/bookings/{bookingId}/preparation-tasks")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "生成课前准备任务切块",
            description = "4 个原子微任务：准备材料/复习笔记/热身题/专注状态；支持无序完成或跳过")
    public List<TaskView> ensureTasks(@PathVariable UUID bookingId) {
        return classroomService.ensurePreparationTasks(bookingId).stream()
                .map(t -> new TaskView(t.getId(), t.getTitle(), t.getSortIndex(), t.getCompletedAt() != null))
                .toList();
    }

    @GetMapping("/bookings/{bookingId}/preparation-tasks")
    @Operation(summary = "查询课前任务清单")
    public List<TaskView> tasks(@PathVariable UUID bookingId) {
        return classroomService.preparationTasks(bookingId).stream()
                .map(t -> new TaskView(t.getId(), t.getTitle(), t.getSortIndex(), t.getCompletedAt() != null))
                .toList();
    }

    @PostMapping("/bookings/{bookingId}/session:end")
    @Operation(summary = "结束课堂会话（导师）", description = "结束后才可标记考勤")
    public java.util.Map<String, String> endSession(@PathVariable UUID bookingId) {
        var session = classroomService.endSession(bookingId);
        return java.util.Map.of("bookingId", bookingId.toString(), "sessionStatus", session.getStatus().name());
    }

    @PostMapping("/bookings/{bookingId}/attendance")
    @Operation(summary = "标记考勤（导师）",
            description = "结课时标记学生出席状态：PRESENT 正常 / LATE 迟到 / ABSENT 缺席；为退款与结算争议提供依据")
    public java.util.Map<String, String> markAttendance(@PathVariable UUID bookingId,
                                                        @RequestBody MarkAttendanceRequest request) {
        var status = ClassroomSession.AttendanceStatus.valueOf(request.attendanceStatus() == null
                ? "PRESENT" : request.attendanceStatus().toUpperCase());
        var session = classroomService.markAttendance(bookingId, status);
        return java.util.Map.of("bookingId", bookingId.toString(),
                "attendanceStatus", session.getAttendanceStatus().name());
    }

    @PostMapping("/preparation-tasks/{taskId}:complete")
    @Operation(summary = "勾选完成微任务", description = "无序完成；跳过不惩罚，随时可重新勾选")
    public TaskView complete(@PathVariable UUID taskId) {
        var task = classroomService.completeTask(taskId);
        return new TaskView(task.getId(), task.getTitle(), task.getSortIndex(), task.getCompletedAt() != null);
    }
}
