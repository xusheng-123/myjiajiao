package com.nurture.student.app;

import com.nurture.student.domain.StudentProfile;
import com.nurture.messaging.app.MessagingApplicationService;
import com.nurture.shared.security.CallerContext;
import com.nurture.student.infra.StudentProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

/**
 * 学生档案应用服务 — 档案生命周期与档案引用（供 parent 模块监护校验消费）。
 * 纯档案域：无跨模块依赖；课程/任务/导师名等跨模块视图由控制器编排（booking/classroom/identity 的 app 服务）。
 */
@Service
public class StudentApplicationService {

    private final StudentProfileRepository profileRepository;
    private final MessagingApplicationService messagingService;
    private final com.nurture.shared.numbering.BusinessNumberGenerator businessNumbers;

    public StudentApplicationService(StudentProfileRepository profileRepository,
                                     MessagingApplicationService messagingService,
                                     com.nurture.shared.numbering.BusinessNumberGenerator businessNumbers) {
        this.profileRepository = profileRepository;
        this.messagingService = messagingService;
        this.businessNumbers = businessNumbers;
    }

    @Transactional
    public StudentProfile ensureProfile(UUID accountId) {
        StudentProfile profile = profileRepository.findByAccountId(accountId)
                .orElseGet(() -> profileRepository.save(new StudentProfile(accountId, nextNo())));
        // 集合在会话内显式初始化：open-in-view=false 下避免游离实体序列化 500
        profile.getPreferredSubjects().size();
        return profile;
    }

    @Transactional
    public StudentProfile updatePreference(UUID accountId, String gradeLevel, String learningStyle,
                                           String focusPreference, Set<String> preferredSubjects) {
        StudentProfile profile = ensureProfile(accountId);
        profile.updatePreference(gradeLevel, learningStyle, focusPreference, preferredSubjects);
        return profile;
    }

    /** 监护绑定事实归 parent 模块（D1/循环切断）：student 侧仅暴露档案引用。 */
    public record StudentRef(UUID profileId, UUID accountId, String studentNo) {
    }

    @Transactional(readOnly = true)
    public java.util.Optional<StudentRef> refByStudentNo(String studentNo) {
        return profileRepository.findByStudentNo(studentNo)
                .map(p -> new StudentRef(p.getId(), p.getAccountId(), p.getStudentNo()));
    }

    @Transactional(readOnly = true)
    public java.util.Optional<StudentRef> refByAccountId(UUID accountId) {
        return profileRepository.findByAccountId(accountId)
                .map(p -> new StudentRef(p.getId(), p.getAccountId(), p.getStudentNo()));
    }

    /** 课后疑难提问：进入与导师的站内会话。 */
    @Transactional
    public void askQuestion(UUID teacherId, String question) {
        UUID studentId = CallerContext.require().accountId();
        var conversation = messagingService.openOrGetId(studentId, teacherId, "课后疑难");
        messagingService.send(conversation, question);
    }

    private String nextNo() {
        return businessNumbers.student();
    }

    /** 学习成长：学生档案 profileId → accountId（不存在返回 null）。 */
    @Transactional(readOnly = true)
    public java.util.UUID profileAccountIdOf(java.util.UUID studentProfileId) {
        return profileRepository.findById(studentProfileId).map(p -> p.getAccountId()).orElse(null);
    }
}
