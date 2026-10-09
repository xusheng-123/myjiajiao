package com.nurture.matching.app;

import com.nurture.matching.domain.StudentDemand;
import com.nurture.matching.infra.FavoriteTeacherRepository;
import com.nurture.matching.infra.StudentDemandRepository;
import com.nurture.review.app.ReviewApplicationService;
import com.nurture.scheduling.app.SchedulingApplicationService;
import com.nurture.shared.config.PlatformConfigReader;
import com.nurture.identity.app.IdentityApplicationService;
import com.nurture.shared.security.CallerContext;
import com.nurture.teacher.app.TeacherApplicationService;
import com.nurture.verification.app.VerificationApplicationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 智能撮合应用服务 — 双入口并存：
 * ① 多条件组合筛选检索（透明推荐理由）；② 学生需求发布（告诉平台我的需求）。
 * 教师卡片提供可解释信任证据：认证状态、评价评分/条数、最近可约时间。
 */
@Service
public class MatchingApplicationService {

    private final TeacherApplicationService teacherService;
    private final VerificationApplicationService verificationService;
    private final ReviewApplicationService reviewService;
    private final SchedulingApplicationService schedulingService;
    private final StudentDemandRepository demandRepository;
    private final com.nurture.matching.infra.FavoriteTeacherRepository favoriteTeacherRepository;
    private final com.nurture.identity.app.IdentityApplicationService identityService;
    private final PlatformConfigReader configReader;

    public MatchingApplicationService(TeacherApplicationService teacherService,
                                      VerificationApplicationService verificationService,
                                      ReviewApplicationService reviewService,
                                      SchedulingApplicationService schedulingService,
                                      StudentDemandRepository demandRepository,
                                      PlatformConfigReader configReader,
            com.nurture.matching.infra.FavoriteTeacherRepository favoriteTeacherRepository,
            com.nurture.identity.app.IdentityApplicationService identityService) {
        this.teacherService = teacherService;
        this.verificationService = verificationService;
        this.reviewService = reviewService;
        this.schedulingService = schedulingService;
        this.demandRepository = demandRepository;
        this.configReader = configReader;
        this.favoriteTeacherRepository = favoriteTeacherRepository;
        this.identityService = identityService;
    }

    // ------------------------------------------------------------ 筛选检索

    @Transactional(readOnly = true)
    public List<TeacherCandidate> search(String subject, String gradeBand, Long maxPricePerHourCents,
                                         String styleTag, String keyword, boolean includeUnverified) {
        int maxSize = (int) configReader.longValue("matching.max-result-size", 20);

        List<TeacherApplicationService.OfferingView> pool = subject != null && !subject.isBlank()
                ? teacherService.searchActiveOfferings(subject)
                : teacherService.searchActiveOfferings(null);

        List<TeacherCandidate> results = new ArrayList<>();
        for (TeacherApplicationService.OfferingView offering : pool) {
            if (gradeBand != null && !gradeBand.isBlank() && !offering.gradeBand().equals(gradeBand)) {
                continue;
            }
            if (maxPricePerHourCents != null && offering.pricePerHourCents() > maxPricePerHourCents) {
                continue;
            }
            // 跨模块教师标识统一为「账户ID」：条目内联档案ID，经 teacher.app 换算
            var brief = teacherService.briefByProfileId(offering.teacherProfileId());
            UUID teacherAccountId = brief.accountId();
            // 教学风格筛选：档案风格标签精确匹配
            if (styleTag != null && !styleTag.isBlank()
                    && (brief.styleTags() == null || !brief.styleTags().contains(styleTag))) {
                continue;
            }
            // 自由关键词：科目/学段/院校包含匹配
            if (keyword != null && !keyword.isBlank()) {
                String haystack = offering.subject() + " " + offering.gradeBand() + " " + brief.school();
                if (!haystack.contains(keyword.trim())) {
                    continue;
                }
            }
            boolean verified = verificationService.isApproved(teacherAccountId);
            if (!includeUnverified && !verified) {
                continue;
            }
            results.add(toCandidate(offering, teacherAccountId, verified));
            if (results.size() >= maxSize) {
                break;
            }
        }
        // 综合透明排序：认证 > 评价证据 > 推荐理由数量；不虚构精确匹配百分比
        results.sort((a, b) -> {
            int byEvidence = Integer.compare(evidenceScore(b), evidenceScore(a));
            return byEvidence != 0 ? byEvidence : Integer.compare(b.reasons().size(), a.reasons().size());
        });
        return results;
    }

    private int evidenceScore(TeacherCandidate c) {
        int score = 0;
        if (c.verificationApproved()) {
            score += 2;
        }
        if (c.reviewCount() > 0) {
            score += 1;
        }
        if (c.nextOpenSlotAt() != null) {
            score += 1;
        }
        return score;
    }

    private TeacherCandidate toCandidate(TeacherApplicationService.OfferingView offering, UUID teacherAccountId, boolean verified) {
        List<String> reasons = new ArrayList<>();
        if (verified) {
            reasons.add("已通过平台资质认证（身份与学历核验）");
        }
        var stats = reviewService.statsOf(teacherAccountId);
        if (stats.reviewCount() > 0) {
            reasons.add(String.format("学员评价 %.1f 分（%d 条评价，可查看原文）", stats.averageRating(), stats.reviewCount()));
        }
        if (offering.pricePerHourCents() <= 20_000) {
            reasons.add("课时定价亲民，适合长期稳定陪跑");
        }
        if (offering.gradeBand() != null) {
            reasons.add("专注 " + offering.gradeBand() + " 学段教学");
        }
        OffsetDateTime nextSlot = schedulingService.openSlotViewsOf(teacherAccountId).stream()
                .map(SchedulingApplicationService.SlotView::startAt)
                .findFirst().orElse(null);
        return new TeacherCandidate(teacherAccountId, offering.subject(), offering.gradeBand(),
                offering.pricePerHourCents(), verified,
                stats.averageRating() > 0 ? Math.round(stats.averageRating() * 10) / 10.0 : null,
                stats.reviewCount(), nextSlot,
                List.of(), reasons);
    }

    // ------------------------------------------------------------ 需求发布

    /** 学生发布学习需求（「告诉平台我的需求」路径）。 */
    @Transactional
    public StudentDemand postDemand(String subject, String gradeBand, String note,
                                    Long budgetMaxCents, String preferredTime) {
        UUID studentId = CallerContext.require().accountId();
        return demandRepository.save(new StudentDemand(studentId, subject, gradeBand, note,
                budgetMaxCents, preferredTime));
    }

    @Transactional(readOnly = true)
    public Page<StudentDemand> myDemands(int page, int size) {
        return demandRepository.findByStudentIdOrderByCreatedAtDesc(CallerContext.require().accountId(),
                PageRequest.of(page, size));
    }

    /** 导师/运营浏览需求池（仅开放中的需求）。 */
    @Transactional(readOnly = true)
    public Page<StudentDemand> openDemands(int page, int size) {
        return demandRepository.findByStatusOrderByCreatedAtDesc(StudentDemand.DemandStatus.OPEN,
                PageRequest.of(page, size));
    }

    @Transactional
    public void closeDemand(UUID demandId) {
        StudentDemand demand = demandRepository.findById(demandId)
                .orElseThrow(() -> new IllegalArgumentException("需求不存在"));
        if (!demand.getStudentId().equals(CallerContext.require().accountId())) {
            throw new org.springframework.security.access.AccessDeniedException("仅需求发布者可以关闭");
        }
        demand.close();
    }

    public record TeacherCandidate(UUID teacherId, String subject, String gradeBand,
                                   long pricePerHourCents, boolean verificationApproved,
                                   Double averageRating, long reviewCount, OffsetDateTime nextOpenSlotAt,
                                   List<String> styleTags, List<String> reasons) {
    }

    // ------------------------------------------------------------ 收藏与规则化推荐

    /** 收藏 / 取消收藏（幂等 toggle）。 */
    @Transactional
    public Map<String, Object> toggleFavorite(UUID studentId, UUID teacherId) {
        var existing = favoriteTeacherRepository.findByStudentIdAndTeacherId(studentId, teacherId);
        if (existing.isPresent()) {
            favoriteTeacherRepository.delete(existing.get());
            return Map.of("teacherId", teacherId.toString(), "favorited", false);
        }
        favoriteTeacherRepository.save(new com.nurture.matching.domain.FavoriteTeacher(studentId, teacherId));
        return Map.of("teacherId", teacherId.toString(), "favorited", true);
    }

    /** 收藏列表。 */
    public List<Map<String, Object>> favoritesOf(UUID studentId) {
        return favoriteTeacherRepository.findByStudentIdOrderByCreatedAtDesc(studentId).stream()
                .map(f -> {
                    Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("teacherId", f.getTeacherId().toString());
                    m.put("displayName", identityService.displayNameOf(f.getTeacherId()).orElse("导师"));
                    m.put("favoritedAt", f.getCreatedAt().toString());
                    return m;
                }).toList();
    }

    /** 规则化教师推荐：逐条可解释理由，不虚构指标。 */
    public List<Map<String, Object>> recommend(String subject, String gradeBand,
                                                Long maxPriceCents, String styleTag) {
        var result = search(subject, gradeBand, maxPriceCents, styleTag, null, false);
        return result.stream().map(t -> {
            List<String> reasons = new ArrayList<>();
            if (subject != null && !subject.isBlank()) reasons.add("擅长" + subject);
            if (t.verificationApproved()) reasons.add("已通过平台资质审核");
            if (gradeBand != null && !gradeBand.isBlank() && t.gradeBand() != null
                    && t.gradeBand().contains(gradeBand)) reasons.add("匹配" + gradeBand + "学段");
            if (maxPriceCents != null && t.pricePerHourCents() <= maxPriceCents) reasons.add("价格符合预算");
            if (styleTag != null && !styleTag.isBlank() && t.styleTags() != null
                    && t.styleTags().contains(styleTag)) reasons.add("教学风格匹配：" + styleTag);
            if (t.averageRating() != null && t.averageRating() >= 4.5)
                reasons.add("评分 " + t.averageRating() + "（" + t.reviewCount() + " 条评价）");
            return Map.of("teacher", t, "reasons", reasons);
        }).toList();
    }
}
