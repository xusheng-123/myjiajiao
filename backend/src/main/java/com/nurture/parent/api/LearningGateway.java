package com.nurture.parent.api;

import com.nurture.learning.app.LearningApplicationService;
import com.nurture.parent.app.ParentApplicationService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/** 家长周报网关 — 校验 ACTIVE 监护绑定后放行（复用 parent 模块既有授权语义）。 */
@Component
public class LearningGateway {

    private final LearningApplicationService learning;
    private final ParentApplicationService parentService;

    public LearningGateway(LearningApplicationService learning, ParentApplicationService parentService) {
        this.learning = learning;
        this.parentService = parentService;
    }

    public Map<String, Object> weeklyReportForParent(UUID parentAccountId, UUID studentId) {
        // ID 语义兼容：children 列表返回的是学生档案 profileId，
        // isActiveGuardian 期望 accountId —— 此处归一化为 accountId 再校验与查询。
        UUID studentAccountId = parentService.accountIdOfStudentProfile(studentId);
        if (studentAccountId == null || !parentService.isActiveGuardian(parentAccountId, studentAccountId)) {
            throw new AccessDeniedException("仅已绑定的孩子可以查看学习周报");
        }
        return learning.weeklyReport(studentAccountId);
    }
}
