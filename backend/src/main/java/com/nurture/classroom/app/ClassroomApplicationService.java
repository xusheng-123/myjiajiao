package com.nurture.classroom.app;

import com.nurture.booking.app.BookingApplicationService;
import com.nurture.classroom.domain.ClassroomSession;
import com.nurture.classroom.domain.PreparationTask;
import com.nurture.classroom.infra.ClassroomSessionRepository;
import com.nurture.classroom.infra.PreparationTaskRepository;
import com.nurture.shared.security.CallerContext;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 课堂协同应用服务 — 会议凭证转发（外部会议 SaaS）、入会前任务清单流转。
 */
@Service
public class ClassroomApplicationService {

    /** ADHD 友好任务切块：课前准备 4 个原子微任务，支持无序完成或跳过。 */
    private static final List<String> DEFAULT_CHUNKS =
            List.of("准备讲义与文具", "花 3 分钟复习上次笔记", "做 1 道热身小题", "调整到专注状态");

    private final ClassroomSessionRepository sessionRepository;
    private final PreparationTaskRepository taskRepository;
    private final BookingApplicationService bookingService;

    public ClassroomApplicationService(ClassroomSessionRepository sessionRepository,
                                       PreparationTaskRepository taskRepository,
                                       BookingApplicationService bookingService) {
        this.sessionRepository = sessionRepository;
        this.taskRepository = taskRepository;
        this.bookingService = bookingService;
    }

    /** 导师为已确认课程创建课堂会话（首版对接外部会议 SaaS）。 */
    @Transactional
    public ClassroomSession ensureSession(UUID bookingId, String meetingUrl) {
        requireParticipant(bookingId);
        return sessionRepository.findByBookingId(bookingId)
                .orElseGet(() -> sessionRepository.save(new ClassroomSession(bookingId, "EXTERNAL_SAAS", meetingUrl)));
    }

    /** 入会通道：返回会议入口；仅参与双方可获取。 */
    @Transactional(readOnly = true)
    public JoinInfo join(UUID bookingId) {
        requireParticipant(bookingId);
        ClassroomSession session = sessionRepository.findByBookingId(bookingId)
                .orElseThrow(() -> new IllegalStateException("课堂尚未就绪：等待导师准备会议链接"));
        return new JoinInfo(session.getMeetingUrl(), session.getMeetingProvider(), session.getStatus().name());
    }

    /** 导师结课时标记学生考勤（正常/迟到/缺席），为结算与退款争议留存事实。 */
    @Transactional
    public ClassroomSession markAttendance(UUID bookingId, ClassroomSession.AttendanceStatus attendanceStatus) {
        requireParticipant(bookingId);
        ClassroomSession session = sessionRepository.findByBookingId(bookingId)
                .orElseThrow(() -> new IllegalStateException("课堂尚未创建，无法标记考勤"));
        session.markAttendance(attendanceStatus);
        return session;
    }

    /** 结束课堂会话（导师操作）；结束后才可标记考勤。 */
    @Transactional
    public ClassroomSession endSession(UUID bookingId) {
        requireParticipant(bookingId);
        ClassroomSession session = sessionRepository.findByBookingId(bookingId)
                .orElseThrow(() -> new IllegalStateException("课堂尚未创建"));
        session.end();
        return session;
    }

    /** 生成课前准备任务清单（幂等：已存在则返回既有清单）。 */
    @Transactional
    public List<PreparationTask> ensurePreparationTasks(UUID bookingId) {
        requireParticipant(bookingId);
        List<PreparationTask> existing = taskRepository.findByBookingIdOrderBySortIndexAsc(bookingId);
        if (!existing.isEmpty()) {
            return existing;
        }
        List<PreparationTask> tasks = new java.util.ArrayList<>();
        for (int i = 0; i < DEFAULT_CHUNKS.size(); i++) {
            tasks.add(taskRepository.save(new PreparationTask(bookingId, DEFAULT_CHUNKS.get(i), i)));
        }
        return tasks;
    }

    /** 任务视图（供 student 控制台专注模式消费）。 */
    public record TaskView(UUID taskId, String title, boolean completed) {
    }

    @Transactional(readOnly = true)
    public List<TaskView> taskViewsOf(UUID bookingId) {
        return taskRepository.findByBookingIdOrderBySortIndexAsc(bookingId).stream()
                .map(t -> new TaskView(t.getId(), t.getTitle(), t.getCompletedAt() != null))
                .toList();
    }

    /** 任务视图（供 student 控制台消费；DTO 化避免实体直出）。 */
    @Transactional(readOnly = true)
    public List<TaskView> preparationTaskViews(UUID bookingId) {
        requireParticipant(bookingId);
        return preparationTasks(bookingId).stream()
                .map(t -> new TaskView(t.getId(), t.getTitle(), t.getCompletedAt() != null))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PreparationTask> preparationTasks(UUID bookingId) {
        requireParticipant(bookingId);
        return taskRepository.findByBookingIdOrderBySortIndexAsc(bookingId);
    }

    /** 无序勾选完成任务（Task Chunking：任务间互不阻塞）。 */
    @Transactional
    public PreparationTask completeTask(UUID taskId) {
        PreparationTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("任务不存在"));
        requireParticipant(task.getBookingId());
        task.complete();
        return task;
    }

    private void requireParticipant(UUID bookingId) {
        var caller = CallerContext.require();
        if (!bookingService.isParticipant(bookingId, caller.accountId())) {
            throw new org.springframework.security.access.AccessDeniedException("仅课程参与双方可访问课堂信息");
        }
    }

    public record JoinInfo(String meetingUrl, String provider, String sessionStatus) {
    }
}
