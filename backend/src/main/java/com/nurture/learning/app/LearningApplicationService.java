package com.nurture.learning.app;

import com.nurture.booking.app.BookingApplicationService;
import com.nurture.identity.app.IdentityApplicationService;
import com.nurture.learning.domain.LearningProfile;
import com.nurture.learning.domain.LearningTask;
import com.nurture.learning.domain.LessonRecord;
import com.nurture.learning.domain.Question;
import com.nurture.learning.infra.LearningProfileRepository;
import com.nurture.learning.infra.LearningTaskRepository;
import com.nurture.learning.infra.LessonRecordRepository;
import com.nurture.learning.infra.QuestionRepository;
import com.nurture.messaging.app.MessagingApplicationService;
import com.nurture.scheduling.app.SchedulingApplicationService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 学习成长应用服务 — 课后任务 / 课堂记录 / 学习档案 / 成长中心 / 家长周报 / 课后答疑。
 * 权限模型：学生仅本人；教师仅「与自己存在授课关系（历史预约）」的学生；
 * 家长仅「监护绑定 ACTIVE」的孩子（复用 parent 模块 isActiveGuardian 语义由调用方校验）。
 */
@Service
public class LearningApplicationService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final LearningTaskRepository taskRepository;
    private final LessonRecordRepository recordRepository;
    private final LearningProfileRepository profileRepository;
    private final QuestionRepository questionRepository;
    private final BookingApplicationService bookingService;
    private final SchedulingApplicationService schedulingService;
    private final MessagingApplicationService messagingService;
    private final IdentityApplicationService identityService;

    public LearningApplicationService(LearningTaskRepository taskRepository,
                                      LessonRecordRepository recordRepository,
                                      LearningProfileRepository profileRepository,
                                      QuestionRepository questionRepository,
                                      BookingApplicationService bookingService,
                                      SchedulingApplicationService schedulingService,
                                      MessagingApplicationService messagingService,
                                      IdentityApplicationService identityService) {
        this.taskRepository = taskRepository;
        this.recordRepository = recordRepository;
        this.profileRepository = profileRepository;
        this.questionRepository = questionRepository;
        this.bookingService = bookingService;
        this.schedulingService = schedulingService;
        this.messagingService = messagingService;
        this.identityService = identityService;
    }

    // ------------------------------------------------------------ 课后任务

    @Transactional
    public Map<String, Object> createTask(UUID teacherId, UUID bookingId, String title,
                                          String description, String priority, Instant deadline) {
        UUID studentId = bookingService.studentOfOwnedBooking(bookingId, teacherId);
        LearningTask task = new LearningTask(bookingId, teacherId, studentId, title,
                description, parsePriority(priority), deadline);
        task = taskRepository.save(task);
        messagingService.notify(studentId, "TASK",
                "新课后任务", "「" + title + "」已发布，记得在截止前完成并提交。");
        return Map.of("taskId", task.getId().toString(), "status", task.getStatus().name(),
                "studentId", studentId.toString());
    }

    @Transactional
    public Map<String, Object> submitTask(UUID studentId, UUID taskId, String content) {
        LearningTask task = taskRepository.locked(taskId)
                .orElseThrow(() -> new IllegalArgumentException("任务不存在"));
        if (!task.getStudentId().equals(studentId)) {
            throw new AccessDeniedException("仅任务归属学生可提交");
        }
        task.submit(content);
        messagingService.notify(task.getTeacherId(), "TASK",
                "课后任务已提交", "「" + task.getTitle() + "」已提交，等待你的反馈。");
        return Map.of("taskId", taskId.toString(), "status", task.getStatus().name());
    }

    @Transactional
    public Map<String, Object> feedbackTask(UUID teacherId, UUID taskId, String feedback) {
        LearningTask task = taskRepository.locked(taskId)
                .orElseThrow(() -> new IllegalArgumentException("任务不存在"));
        if (!task.getTeacherId().equals(teacherId)) {
            throw new AccessDeniedException("仅任务归属教师可反馈");
        }
        task.feedback(feedback);
        messagingService.notify(task.getStudentId(), "TASK",
                "任务已批改", "「" + task.getTitle() + "」有新的教师反馈，点击查看。");
        return Map.of("taskId", taskId.toString(), "status", task.getStatus().name());
    }

    public List<Map<String, Object>> tasksOfStudent(UUID studentId, String statusFilter) {
        List<LearningTask> tasks = (statusFilter == null || statusFilter.isBlank())
                ? taskRepository.findByStudentIdOrderByCreatedAtDesc(studentId)
                : taskRepository.findByStudentIdAndStatus(studentId, LearningTask.Status.valueOf(statusFilter));
        return tasks.stream().map(this::taskView).toList();
    }

    public List<Map<String, Object>> tasksOfTeacher(UUID teacherId) {
        return taskRepository.findByTeacherIdOrderByCreatedAtDesc(teacherId).stream()
                .map(this::taskView).toList();
    }

    private Map<String, Object> taskView(LearningTask t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("taskId", t.getId().toString());
        m.put("bookingId", t.getBookingId().toString());
        m.put("title", t.getTitle());
        m.put("description", t.getDescription());
        m.put("priority", t.getPriority().name());
        m.put("deadline", t.getDeadline() == null ? null : t.getDeadline().toString());
        m.put("status", (t.overdue() ? "OVERDUE_" : "") + t.getStatus().name());
        m.put("submission", t.getSubmissionContent());
        m.put("submittedAt", t.getSubmittedAt() == null ? null : t.getSubmittedAt().toString());
        m.put("feedback", t.getFeedback());
        return m;
    }

    // ------------------------------------------------------------ 课堂记录

    @Transactional
    public Map<String, Object> createRecord(UUID teacherId, UUID bookingId, String subject,
                                            String knowledgePoints, String performance,
                                            String problems, String nextSuggestion, String note) {
        UUID studentId = bookingService.studentOfOwnedBooking(bookingId, teacherId);
        if (recordRepository.findByBookingId(bookingId).isPresent()) {
            throw new IllegalStateException("该课程已有课堂记录，请勿重复创建");
        }
        LessonRecord r = recordRepository.save(new LessonRecord(bookingId, teacherId, studentId,
                subject, knowledgePoints, performance, problems, nextSuggestion, note));
        messagingService.notify(studentId, "LESSON_RECORD",
                "课堂记录已生成", "老师更新了你的课堂记录与下节建议，记得查看学习档案。");
        return Map.of("recordId", r.getId().toString(), "bookingId", bookingId.toString());
    }

    public List<Map<String, Object>> recordsOfStudent(UUID studentId) {
        return recordRepository.findByStudentIdOrderByCreatedAtDesc(studentId).stream()
                .map(this::recordView).toList();
    }

    public List<Map<String, Object>> recordsOfTeacher(UUID teacherId) {
        return recordRepository.findByTeacherIdOrderByCreatedAtDesc(teacherId).stream()
                .map(this::recordView).toList();
    }

    private Map<String, Object> recordView(LessonRecord r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("recordId", r.getId().toString());
        m.put("bookingId", r.getBookingId().toString());
        m.put("subject", r.getSubject());
        m.put("knowledgePoints", r.getKnowledgePoints());
        m.put("performance", r.getPerformance());
        m.put("problems", r.getProblems());
        m.put("nextSuggestion", r.getNextSuggestion());
        m.put("note", r.getNote());
        m.put("createdAt", r.getCreatedAt().toString());
        return m;
    }

    // ------------------------------------------------------------ 学习档案

    @Transactional
    public LearningProfile ensureProfile(UUID studentId) {
        return profileRepository.findByStudentId(studentId)
                .orElseGet(() -> profileRepository.save(new LearningProfile(studentId, null, null, null)));
    }

    @Transactional
    public LearningProfile updateProfile(UUID studentId, String goal, String stage, String targetSubjects) {
        LearningProfile p = ensureProfile(studentId);
        p.update(goal, stage, targetSubjects);
        return p;
    }

    /** 学习档案：画像 + 本周/累计统计 + 课堂记录 + 教学历史。 */
    public Map<String, Object> archiveOf(UUID studentId) {
        ensureProfile(studentId);
        LearningProfile p = profileRepository.findByStudentId(studentId).orElseThrow();
        var bookings = bookingService.studentSnapshots(studentId, PageRequest.of(0, 200)).getContent();
        long completed = bookings.stream().filter(b -> "COMPLETED".equals(b.getStatus())).count();
        List<Map<String, Object>> history = bookings.stream()
                .map(b -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("bookingId", b.getId());
                    m.put("subject", b.getSubject());
                    m.put("status", b.getStatus());

                    m.put("teacherName", identityService.displayNameOf(b.getTeacherId()).orElse("导师"));
                    return m;
                }).toList();
        List<Map<String, Object>> records = recordsOfStudent(studentId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("profile", Map.of(
                "goal", p.getGoal() == null ? "" : p.getGoal(),
                "stage", p.getStage() == null ? "" : p.getStage(),
                "targetSubjects", p.getTargetSubjects() == null ? "" : p.getTargetSubjects()));
        out.put("stats", Map.of(
                "completedLessons", completed,
                "totalLessons", bookings.size(),
                "taskCompleted", countTasks(studentId, LearningTask.Status.COMPLETED),
                "taskTotal", countTasks(studentId, null),
                "lessonRecords", records.size()));
        out.put("lessonRecords", records);
        out.put("history", history);
        return out;
    }

    private int countTasks(UUID studentId, LearningTask.Status status) {
        return status == null
                ? taskRepository.findByStudentIdOrderByCreatedAtDesc(studentId).size()
                : taskRepository.findByStudentIdAndStatus(studentId, status).size();
    }

    // ------------------------------------------------------------ 成长中心 / 周报聚合

    /** 周（周一 00:00 起的 Asia/Shanghai 自然周）。 */
    private Instant weekStart() {
        var now = Instant.now().atZone(ZONE);
        return now.with(java.time.temporal.ChronoField.NANO_OF_DAY, 0)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toInstant();
    }

    /** 学习成长中心聚合：下一课 + 今日任务 + 本周统计 + 最近动态。空数据安全。 */
    public Map<String, Object> growthOf(UUID studentId) {
        Instant weekStart = weekStart();
        var upcoming = bookingService.upcomingForStudent(studentId).stream()
                .filter(b -> "CONFIRMED".equals(b.status()) || "IN_PROGRESS".equals(b.status()))
                .toList();
        Map<String, Object> next = null;
        if (!upcoming.isEmpty()) {
            var n = upcoming.getFirst();
            next = Map.of("bookingId", n.bookingId(), "subject", n.subject(),
                    "teacherName", identityService.displayNameOf(n.teacherId()).orElse("导师"),
                    "startAt", n.startAt() == null ? null : n.startAt().toString(),
                    "status", n.status());
        }
        List<LearningTask> tasks = taskRepository.findByStudentIdOrderByCreatedAtDesc(studentId);
        var today = Instant.now().atZone(ZONE).toLocalDate();
        List<Map<String, Object>> todayTasks = tasks.stream()
                .filter(t -> t.getDeadline() != null
                        && t.getDeadline().atZone(ZONE).toLocalDate().equals(today)
                        && t.getStatus() != LearningTask.Status.COMPLETED)
                .map(this::taskView).toList();
        // 本周已完成课程（按 slot 结束时间落在本周）
        List<LearningTask> ftasks = tasks;
        long weekLessons = bookingService.studentSnapshots(studentId, PageRequest.of(0, 200)).getContent().stream()
                .filter(b -> "COMPLETED".equals(b.getStatus()))
                .filter(b -> {
                    try {
                        var slot = schedulingService.slotOf(b.getSlotId());
                        return slot.isPresent() && slot.get().endAt().toInstant().isAfter(weekStart);
                    } catch (Exception e) {
                        return false;
                    }
                }).count();
        double minutes = bookingService.studentSnapshots(studentId, PageRequest.of(0, 200)).getContent().stream()
                .filter(b -> "COMPLETED".equals(b.getStatus()))
                .filter(b -> {
                    try {
                        var slot = schedulingService.slotOf(b.getSlotId());
                        return slot.isPresent() && slot.get().endAt().toInstant().isAfter(weekStart);
                    } catch (Exception e) {
                        return false;
                    }
                })
                .mapToLong(b -> {
                    try {
                        var slot = schedulingService.slotOf(b.getSlotId());
                        return slot.map(sv -> Duration.between(sv.startAt(), sv.endAt()).toMinutes()).orElse(0L);
                    } catch (Exception e) {
                        return 0L;
                    }
                }).sum();
        long weekTaskDone = ftasks.stream()
                .filter(t -> t.getStatus() == LearningTask.Status.COMPLETED
                        && t.getFeedbackAt() != null && t.getFeedbackAt().isAfter(weekStart)).count();
        long weekTaskAll = ftasks.stream()
                .filter(t -> t.getFeedbackAt() == null || t.getFeedbackAt().isAfter(weekStart)
                        || t.getCreatedAt().isAfter(weekStart)).count();
        // 最近动态：课堂记录 + 任务反馈，各取最新 3 条
        List<Map<String, Object>> recent = new ArrayList<>();
        recordRepository.findByStudentIdOrderByCreatedAtDesc(studentId).stream().limit(3)
                .forEach(r -> recent.add(Map.of("type", "LESSON_RECORD",
                        "text", "课堂记录：" + (r.getSubject() == null ? "" : r.getSubject()),
                        "at", r.getCreatedAt().toString())));
        taskRepository.findByStudentIdOrderByCreatedAtDesc(studentId).stream()
                .filter(t -> t.getFeedback() != null).limit(3)
                .forEach(t -> recent.add(Map.of("type", "TASK_FEEDBACK",
                        "text", "任务反馈：" + t.getTitle(), "at", t.getFeedbackAt().toString())));
        recent.sort((a, b) -> ((String) a.get("at")).compareTo((String) b.get("at")));
        java.util.Collections.reverse(recent);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("nextLesson", next);
        out.put("todayTasks", todayTasks);
        out.put("week", Map.of(
                "lessonsCompleted", weekLessons,
                "minutes", minutes,
                "taskCompleted", weekTaskDone,
                "taskTotal", Math.max(weekTaskAll, weekTaskDone)));
        out.put("streakDays", streakOf(studentId));
        out.put("recent", recent.stream().limit(5).toList());
        return out;
    }

    /** 连续学习天数：连续自然日中存在已完成课程（按 slot 结束日），最多回看 30 天。 */
    private int streakOf(UUID studentId) {
        var days = bookingService.studentSnapshots(studentId, PageRequest.of(0, 200)).getContent().stream()
                .filter(b -> "COMPLETED".equals(b.getStatus()))
                .map(b -> {
                    try {
                        var slot = schedulingService.slotOf(b.getSlotId());
                        return slot.map(sv -> sv.endAt().toInstant().atZone(ZONE).toLocalDate()).orElse(null);
                    } catch (Exception e) {
                        return null;
                    }
                }).filter(d -> d != null).distinct().sorted().toList();
        if (days.isEmpty()) return 0;
        int streak = 1;
        var today = Instant.now().atZone(ZONE).toLocalDate();
        if (!days.contains(today) && !days.contains(today.minusDays(1))) return 0;
        var cur = days.contains(today) ? today : today.minusDays(1);
        for (int i = days.size() - 1; i >= 0; i--) {
            if (days.get(i).equals(cur)) {
                continue;
            }
            if (days.get(i).equals(cur.minusDays(1))) {
                cur = cur.minusDays(1);
                streak++;
            } else {
                break;
            }
        }
        return streak;
    }

    /** 家长学习周报：仅 ACTIVE 绑定的孩子；结构化真实数据，无虚构指标。 */
    public Map<String, Object> weeklyReport(UUID studentId) {
        Instant weekStart = weekStart();
        var weekBookings = bookingService.studentSnapshots(studentId, PageRequest.of(0, 200)).getContent().stream()
                .filter(b -> {
                    try {
                        var slot = schedulingService.slotOf(b.getSlotId());
                        return slot.isPresent() && !slot.get().startAt().toInstant().isBefore(weekStart);
                    } catch (Exception e) {
                        return false;
                    }
                }).toList();
        double minutes = 0;
        long lessons = weekBookings.size();
        for (var b : weekBookings) {
            try {
                var slot = schedulingService.slotOf(b.getSlotId());
                if (slot.isPresent()) minutes += Duration.between(slot.get().startAt(), slot.get().endAt()).toMinutes();
            } catch (Exception ignored) { }
        }
        var tasks = taskRepository.findByStudentIdOrderByCreatedAtDesc(studentId).stream()
                .filter(t -> t.getSubmittedAt() == null || t.getSubmittedAt().isAfter(weekStart)
                        || t.getCreatedAt().isAfter(weekStart)).toList();
        long done = tasks.stream().filter(t -> t.getStatus() == LearningTask.Status.COMPLETED).count();
        List<Map<String, Object>> feedbacks = recordRepository
                .findByStudentIdOrderByCreatedAtDesc(studentId).stream().limit(3)
                .<Map<String, Object>>map(r -> Map.of("subject", (Object) (r.getSubject() == null ? "" : r.getSubject()),
                        "performance", r.getPerformance() == null ? "" : r.getPerformance(),
                        "nextSuggestion", r.getNextSuggestion() == null ? "" : r.getNextSuggestion()))
                .toList();
        var upcoming = bookingService.upcomingForStudent(studentId).stream()
                .filter(b -> "CONFIRMED".equals(b.status())).limit(3)
                .<Map<String, Object>>map(b -> Map.of("subject", (Object) b.subject(),
                        "startAt", (Object) (b.startAt() == null ? null : b.startAt().toString()))).toList();
        return Map.of(
                "weekLessons", lessons,
                "weekMinutes", minutes,
                "taskCompleted", done,
                "taskTotal", tasks.size(),
                "teacherFeedbacks", feedbacks,
                "upcomingLessons", upcoming);
    }

    // ------------------------------------------------------------ 课后答疑

    @Transactional
    public Map<String, Object> askQuestion(UUID studentId, UUID teacherId, String subject,
                                           String title, String content) {
        if (!bookingService.hasTeachingRelation(teacherId, studentId)) {
            throw new AccessDeniedException("只能向曾经或正在为你授课的教师提问");
        }
        Question q = questionRepository.save(new Question(studentId, teacherId, subject, title, content));
        messagingService.notify(teacherId, "QUESTION",
                "新的课后答疑", "学生提出了一个问题：「" + title + "」，点击查看并回答。");
        return Map.of("questionId", q.getId().toString(), "status", q.getStatus().name());
    }

    @Transactional
    public Map<String, Object> answerQuestion(UUID teacherId, UUID questionId, String answer) {
        Question q = questionRepository.locked(questionId)
                .orElseThrow(() -> new IllegalArgumentException("问题不存在"));
        if (!q.getTeacherId().equals(teacherId)) {
            throw new AccessDeniedException("仅提问归属教师可回答");
        }
        q.answer(answer);
        messagingService.notify(q.getStudentId(), "QUESTION",
                "课后答疑已回答", "你的问题「" + q.getTitle() + "」已被老师回答。");
        return Map.of("questionId", questionId.toString(), "status", q.getStatus().name());
    }

    @Transactional
    public Map<String, Object> closeQuestion(UUID userId, UUID questionId) {
        Question q = questionRepository.locked(questionId)
                .orElseThrow(() -> new IllegalArgumentException("问题不存在"));
        if (!q.getStudentId().equals(userId) && !q.getTeacherId().equals(userId)) {
            throw new AccessDeniedException("仅参与双方可关闭该问题");
        }
        q.close();
        return Map.of("questionId", questionId.toString(), "status", q.getStatus().name());
    }

    public List<Map<String, Object>> questionsOfStudent(UUID studentId) {
        return questionRepository.findByStudentIdOrderByCreatedAtDesc(studentId).stream()
                .map(this::questionView).toList();
    }

    public List<Map<String, Object>> questionsOfTeacher(UUID teacherId, String status) {
        var list = (status == null || status.isBlank())
                ? questionRepository.findByTeacherIdOrderByCreatedAtDesc(teacherId)
                : questionRepository.findByTeacherIdAndStatusOrderByCreatedAtDesc(teacherId,
                        Question.Status.valueOf(status));
        return list.stream().map(this::questionView).toList();
    }

    private Map<String, Object> questionView(Question q) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("questionId", q.getId().toString());
        m.put("subject", q.getSubject());
        m.put("title", q.getTitle());
        m.put("content", q.getContent());
        m.put("status", q.getStatus().name());
        m.put("answer", q.getAnswer());
        m.put("createdAt", q.getCreatedAt().toString());
        return m;
    }

    private static LearningTask.Priority parsePriority(String p) {
        try {
            return p == null ? LearningTask.Priority.NORMAL : LearningTask.Priority.valueOf(p);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("优先级仅支持 LOW / NORMAL / HIGH");
        }
    }

    /** 教师的学生列表（去重），供前端选择查看档案。 */
    public java.util.List<Map<String, Object>> studentsOfTeacher(UUID teacherId) {
        return bookingService.studentsOfTeacher(teacherId).stream()
                .map(sid -> {
                    Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("studentId", sid.toString());
                    m.put("displayName", identityService.displayNameOf(sid).orElse("学生"));
                    return m;
                }).toList();
    }

    /** 授权校验：教师须与该学生存在授课关系。 */
    public void requireTeachingRelation(UUID teacherId, UUID studentId) {
        if (!bookingService.hasTeachingRelation(teacherId, studentId)) {
            throw new org.springframework.security.access.AccessDeniedException("仅授课过的教师可以查看该学生学习档案");
        }
    }
}
