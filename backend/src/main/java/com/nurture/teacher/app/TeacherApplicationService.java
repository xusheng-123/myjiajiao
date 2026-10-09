package com.nurture.teacher.app;

import com.nurture.teacher.domain.SubjectOffering;
import com.nurture.teacher.domain.TeacherProfile;
import com.nurture.teacher.infra.SubjectOfferingRepository;
import com.nurture.teacher.infra.TeacherProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 教师领域应用服务 — 资质档案、擅长科目与定价管理。
 */
@Service
public class TeacherApplicationService {

    private final TeacherProfileRepository teacherProfileRepository;
    private final SubjectOfferingRepository offeringRepository;

    public TeacherApplicationService(TeacherProfileRepository teacherProfileRepository,
                                     SubjectOfferingRepository offeringRepository) {
        this.teacherProfileRepository = teacherProfileRepository;
        this.offeringRepository = offeringRepository;
    }

    @Transactional
    public TeacherProfile ensureProfile(UUID accountId) {
        return teacherProfileRepository.findByAccountId(accountId)
                .orElseGet(() -> teacherProfileRepository.save(new TeacherProfile(accountId, nextNo())));
    }

    @Transactional
    public TeacherProfile updateProfile(UUID accountId, String school, String highestDegree,
                                        Integer yearsOfTeaching, String intro, Set<String> styleTags) {
        TeacherProfile profile = ensureProfile(accountId);
        profile.update(school, highestDegree, yearsOfTeaching, intro, styleTags);
        return profile;
    }

    @Transactional
    /** 档案摘要（供 matching 检索消费：账户ID/院校/风格标签）。 */
    public record TeacherBrief(UUID accountId, String school, java.util.Set<String> styleTags) {
    }

    @Transactional(readOnly = true)
    public TeacherBrief briefByProfileId(UUID teacherProfileId) {
        TeacherProfile profile = teacherProfileRepository.findById(teacherProfileId)
                .orElseThrow(() -> new IllegalArgumentException("导师档案不存在"));
        return new TeacherBrief(profile.getAccountId(), profile.getSchool(), profile.getTeachingStyleTags());
    }

    /** 在售开课条目检索（供 matching 消费；subject 近似匹配）。 */
    public record OfferingView(java.util.UUID offeringId, java.util.UUID teacherProfileId, String subject,
                               String gradeBand, long pricePerHourCents, boolean active) {
    }

    @Transactional(readOnly = true)
    public List<OfferingView> searchActiveOfferings(String subject) {
        return offeringRepository.searchBySimilarSubject(subject).stream()
                .filter(SubjectOffering::isActive)
                .map(o -> new OfferingView(o.getId(), o.getTeacherId(), o.getSubject(),
                        o.getGradeBand(), o.getPricePerHourCents(), o.isActive()))
                .toList();
    }

    @Transactional(readOnly = true)
    /** 开课条目视图（供 order 定价消费）。 */
    public record OfferingPriceView(String subject, long pricePerHourCents) {
    }

    @Transactional(readOnly = true)
    public List<OfferingPriceView> offeringsPriceOf(UUID teacherAccountId) {
        // 修复：accountId 是账户 ID，需先换算为 TeacherProfile.id 才能查到条目
        var profile = teacherProfileRepository.findByAccountId(teacherAccountId).orElse(null);
        if (profile == null) return List.of();
        return offeringRepository.findByTeacherIdAndActiveTrue(profile.getId()).stream()
                .map(o -> new OfferingPriceView(o.getSubject(), o.getPricePerHourCents()))
                .toList();
    }

    public long countProfiles() {
        return teacherProfileRepository.count();
    }

    public SubjectOffering addSubject(UUID accountId, String subject, String gradeBand, long pricePerHourCents) {
        TeacherProfile profile = ensureProfile(accountId);
        // D9 幂等：同导师+科目+学段的在售条目唯一，重复创建返回既有条目（价格调整请走 reprice）
        var existing = offeringRepository
                .findByTeacherIdAndSubjectAndGradeBandAndActiveTrue(profile.getId(), subject, gradeBand);
        if (existing.isPresent()) {
            return existing.get();
        }
        return offeringRepository.save(new SubjectOffering(profile.getId(), subject, gradeBand, pricePerHourCents));
    }

    @Transactional
    public SubjectOffering reprice(UUID accountId, UUID offeringId, long newPricePerHourCents) {
        SubjectOffering offering = ownedOffering(accountId, offeringId);
        offering.reprice(newPricePerHourCents);
        return offering;
    }

    @Transactional
    public void deactivateSubject(UUID accountId, UUID offeringId) {
        ownedOffering(accountId, offeringId).deactivate();
    }

    @Transactional(readOnly = true)
    public List<SubjectOffering> offeringsOf(UUID accountId) {
        TeacherProfile profile = ensureProfile(accountId);
        return offeringRepository.findByTeacherIdAndActiveTrue(profile.getId());
    }

    @Transactional(readOnly = true)
    public TeacherProfile byAccountId(UUID accountId) {
        return ensureProfile(accountId);
    }

    @Transactional(readOnly = true)
    public TeacherProfile byTeacherId(UUID teacherId) {
        return teacherProfileRepository.findById(teacherId)
                .orElseThrow(() -> new IllegalArgumentException("导师不存在"));
    }

    private SubjectOffering ownedOffering(UUID accountId, UUID offeringId) {
        TeacherProfile profile = ensureProfile(accountId);
        SubjectOffering offering = offeringRepository.findById(offeringId)
                .orElseThrow(() -> new IllegalArgumentException("开课条目不存在"));
        if (!offering.getTeacherId().equals(profile.getId())) {
            throw new org.springframework.security.access.AccessDeniedException("仅开课导师可变更该条目");
        }
        return offering;
    }

    private String nextNo() {
        return "NUR-TEA-" + String.format("%06d", 1 + new java.security.SecureRandom().nextInt(999_999));
    }
}
