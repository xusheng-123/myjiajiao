package com.nurture.parent.infra;

import com.nurture.parent.domain.GuardianBinding;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GuardianBindingRepository extends JpaRepository<GuardianBinding, UUID> {

    List<GuardianBinding> findByParentIdAndStatus(UUID parentId, GuardianBinding.BindingStatus status);

    List<GuardianBinding> findByStudentId(UUID studentId);

    Optional<GuardianBinding> findByParentIdAndStudentId(UUID parentId, UUID studentId);
}
