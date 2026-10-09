
package com.nurture.parent.api;

import com.nurture.parent.app.ParentApplicationService;
import com.nurture.shared.security.CallerContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** 家长 · 学习周报 — 仅 ACTIVE 绑定的孩子；数据全部来自真实课程 / 任务 / 课堂记录。 */
@RestController
@RequestMapping("/api/v1/parent/me")
@Tag(name = "parent-weekly / 家长学习周报")
@SecurityRequirement(name = "bearerJWT")
@PreAuthorize("hasRole('PARENT') or hasRole('ADMIN')")
public class ParentWeeklyController {

    private final LearningGateway learning;

    public ParentWeeklyController(LearningGateway learning) {
        this.learning = learning;
    }

    @GetMapping("/weekly-report")
    @Operation(summary = "孩子的学习周报（本周课程 / 时长 / 任务完成率 / 教师反馈 / 下周课程）")
    public Object weeklyReport(@RequestParam UUID studentId) {
        return learning.weeklyReportForParent(CallerContext.require().accountId(), studentId);
    }
}
