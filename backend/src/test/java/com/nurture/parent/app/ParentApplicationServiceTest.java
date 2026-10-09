package com.nurture.parent.app;

import com.nurture.identity.app.IdentityApplicationService;
import com.nurture.parent.domain.GuardianBinding;
import com.nurture.parent.domain.ParentProfile;
import com.nurture.parent.infra.FamilyWalletRepository;
import com.nurture.parent.infra.GuardianBindingRepository;
import com.nurture.parent.infra.ParentProfileRepository;
import com.nurture.parent.infra.WalletTransactionRepository;
import com.nurture.student.app.StudentApplicationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 监护绑定（D1 收口）单元测试：PENDING 建立 / 学生确认激活 / 重复绑定拒绝。
 * GuardianBinding 已从 student 模块迁移至 parent 模块（打破 student ↔ parent 循环）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ParentApplicationServiceTest {

    @Mock ParentProfileRepository parentProfileRepository;
    @Mock FamilyWalletRepository walletRepository;
    @Mock WalletTransactionRepository txnRepository;
    @Mock GuardianBindingRepository bindingRepository;
    @Mock StudentApplicationService studentService;
    @Mock IdentityApplicationService identityService;

    private ParentApplicationService service;
    private final UUID parentAccountId = UUID.randomUUID();
    private final UUID studentProfileId = UUID.randomUUID();
    private final UUID studentAccountId = UUID.randomUUID();
    private ParentProfile parentProfile;

    @BeforeEach
    void setUp() {
        service = new ParentApplicationService(parentProfileRepository, walletRepository,
                txnRepository, bindingRepository, studentService, identityService);
        parentProfile = new ParentProfile(parentAccountId, "PAR0001");
        lenient().when(parentProfileRepository.findByAccountId(parentAccountId))
                .thenReturn(Optional.of(parentProfile));
        lenient().when(parentProfileRepository.save(any(ParentProfile.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(bindingRepository.save(any(GuardianBinding.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(studentService.refByStudentNo("S2026001"))
                .thenReturn(Optional.of(new StudentApplicationService.StudentRef(
                        studentProfileId, studentAccountId, "S2026001")));
    }

    @Test
    @DisplayName("家长提交学生编号绑定子女 → 建立 PENDING 关联")
    void bindChildCreatesPendingBinding() {
        GuardianBinding binding = service.bindChild(parentAccountId, "S2026001", "母亲");

        assertThat(binding.getParentId()).isEqualTo(parentProfile.getId());
        assertThat(binding.getStudentId()).isEqualTo(studentProfileId);
        assertThat(binding.getStatus()).isEqualTo(GuardianBinding.BindingStatus.PENDING);
    }

    @Test
    @DisplayName("重复绑定同一学生 → 拒绝并提示")
    void bindChildRejectsDuplicate() {
        when(bindingRepository.findByParentIdAndStudentId(parentProfile.getId(), studentProfileId))
                .thenReturn(Optional.of(new GuardianBinding(parentProfile.getId(), studentProfileId, "母亲")));

        assertThatThrownBy(() -> service.bindChild(parentAccountId, "S2026001", "母亲"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("已绑定");
    }

    @Test
    @DisplayName("学生编号不存在 → 拒绝绑定")
    void bindChildRejectsUnknownStudentNo() {
        assertThatThrownBy(() -> service.bindChild(parentAccountId, "NOPE", "母亲"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("学生编号不存在");
    }

    @Test
    @DisplayName("学生确认绑定 → PENDING 转 ACTIVE")
    void confirmBindingActivatesPending() {
        GuardianBinding pending = new GuardianBinding(parentProfile.getId(), studentProfileId, "母亲");
        when(studentService.refByAccountId(studentAccountId))
                .thenReturn(Optional.of(new StudentApplicationService.StudentRef(
                        studentProfileId, studentAccountId, "S2026001")));
        when(bindingRepository.findByStudentId(studentProfileId)).thenReturn(List.of(pending));

        GuardianBinding activated = service.confirmBinding(studentAccountId);

        assertThat(activated.getStatus()).isEqualTo(GuardianBinding.BindingStatus.ACTIVE);
    }

    @Test
    @DisplayName("isActiveGuardian：ACTIVE 绑定 → true；无绑定 → false")
    void isActiveGuardianChecksActiveStatusOnly() {
        GuardianBinding active = new GuardianBinding(parentProfile.getId(), studentProfileId, "母亲");
        active.activate();
        when(bindingRepository.findByParentIdAndStudentId(parentProfile.getId(), studentProfileId))
                .thenReturn(Optional.of(active));
        when(studentService.refByAccountId(studentAccountId))
                .thenReturn(Optional.of(new StudentApplicationService.StudentRef(
                        studentProfileId, studentAccountId, "S2026001")));

        assertThat(service.isActiveGuardian(parentAccountId, studentAccountId)).isTrue();

        when(bindingRepository.findByParentIdAndStudentId(parentProfile.getId(), studentProfileId))
                .thenReturn(Optional.empty());
        assertThat(service.isActiveGuardian(parentAccountId, studentAccountId)).isFalse();
    }
}
