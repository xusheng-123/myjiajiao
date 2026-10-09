package com.nurture.matching.api;

import com.nurture.matching.app.MatchingApplicationService;
import com.nurture.matching.domain.StudentDemand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * 智能撮合 API — 双入口：组合筛选检索 + 需求发布。
 */
@RestController
@RequestMapping("/api/v1/matching")
@Tag(name = "matching / 智能撮合", description = "师资检索、需求发布、透明推荐理由、评价证据")
@SecurityRequirement(name = "bearerJWT")
public class MatchingController {

    private final MatchingApplicationService matchingService;

    public MatchingController(MatchingApplicationService matchingService) {
        this.matchingService = matchingService;
    }

    public record PostDemandRequest(@NotBlank String subject, String gradeBand, String note,
                                    Long budgetMaxCents, String preferredTime) {
    }

    public record DemandView(UUID demandId, String subject, String gradeBand, String note,
                             Long budgetMaxCents, String preferredTime, String status) {
        static DemandView from(StudentDemand d) {
            return new DemandView(d.getId(), d.getSubject(), d.getGradeBand(), d.getNote(),
                    d.getBudgetMaxCents(), d.getPreferredTime(), d.getStatus().name());
        }
    }

    @GetMapping("/teachers:search")
    @Operation(summary = "找老师 · 组合筛选",
            description = "每条结果附带透明推荐理由与信任证据（认证状态、评价评分/条数、最近可约时间）；"
                    + "不提供神秘匹配百分比，用户可随时清空条件查看全部")
    public List<MatchingApplicationService.TeacherCandidate> search(
            @Parameter(description = "科目，如 数学") @RequestParam(required = false) String subject,
            @Parameter(description = "学段，如 初中") @RequestParam(required = false) String gradeBand,
            @Parameter(description = "单价上限（分/小时）") @RequestParam(required = false) Long maxPricePerHourCents,
            @Parameter(description = "教学风格标签") @RequestParam(required = false) String styleTag,
            @Parameter(description = "自由关键词") @RequestParam(required = false) String keyword,
            @Parameter(description = "是否包含未认证导师（默认仅展示已认证）") @RequestParam(defaultValue = "false") boolean includeUnverified) {
        return matchingService.search(subject, gradeBand, maxPricePerHourCents, styleTag, keyword, includeUnverified);
    }

    @PreAuthorize("hasRole('STUDENT') or hasRole('PARENT') or hasRole('ADMIN')")
    @PostMapping("/demands")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "发布我的学习需求",
            description = "「告诉平台我的需求」路径：描述科目/学段/预算/方便时间，与筛选检索并存")
    public DemandView postDemand(@RequestBody @jakarta.validation.Valid PostDemandRequest request) {
        return DemandView.from(matchingService.postDemand(request.subject(), request.gradeBand(),
                request.note(), request.budgetMaxCents(), request.preferredTime()));
    }

    @GetMapping("/demands")
    @Operation(summary = "我发布的需求列表")
    public Page<DemandView> myDemands(@RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "10") int size) {
        return matchingService.myDemands(page, size).map(DemandView::from);
    }

    @GetMapping("/demands:open")
    @Operation(summary = "需求池（导师/运营浏览开放中的需求）")
    public Page<DemandView> openDemands(@RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "10") int size) {
        return matchingService.openDemands(page, size).map(DemandView::from);
    }

    @PreAuthorize("hasRole('STUDENT') or hasRole('PARENT') or hasRole('ADMIN')")
    @DeleteMapping("/demands/{demandId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "关闭我的需求")
    public void closeDemand(@PathVariable java.util.UUID demandId) {
        matchingService.closeDemand(demandId);
    }

    @GetMapping("/favorites")
    @Operation(summary = "我的收藏教师列表")
    public Object favorites() {
        return matchingService.favoritesOf(com.nurture.shared.security.CallerContext.require().accountId());
    }

    @PostMapping("/teachers/{teacherId}:favorite")
    @Operation(summary = "收藏 / 取消收藏教师（幂等 toggle）")
    public java.util.Map<String, Object> toggleFavorite(@org.springframework.web.bind.annotation.PathVariable java.util.UUID teacherId) {
        return matchingService.toggleFavorite(com.nurture.shared.security.CallerContext.require().accountId(), teacherId);
    }

    @GetMapping("/teachers:recommend")
    @Operation(summary = "规则化教师推荐（可解释理由）")
    public Object recommend(@org.springframework.web.bind.annotation.RequestParam(required = false) String subject,
                            @org.springframework.web.bind.annotation.RequestParam(required = false) String gradeBand,
                            @org.springframework.web.bind.annotation.RequestParam(required = false) Long maxPriceCents,
                            @org.springframework.web.bind.annotation.RequestParam(required = false) String styleTag) {
        return matchingService.recommend(subject, gradeBand, maxPriceCents, styleTag);
    }
}
