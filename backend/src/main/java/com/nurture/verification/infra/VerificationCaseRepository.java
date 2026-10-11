package com.nurture.verification.infra;

import com.nurture.verification.domain.VerificationCase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface VerificationCaseRepository extends JpaRepository<VerificationCase, UUID> {

    Optional<VerificationCase> findFirstByTeacherIdOrderBySubmittedAtDescIdDesc(UUID teacherId);

    Page<VerificationCase> findByStatus(VerificationCase.VerificationStatus status, Pageable pageable);

    Page<VerificationCase> findByOrderBySubmittedAtDesc(Pageable pageable);
}
