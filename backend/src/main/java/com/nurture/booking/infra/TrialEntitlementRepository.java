package com.nurture.booking.infra;

import com.nurture.booking.domain.TrialEntitlement;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface TrialEntitlementRepository extends JpaRepository<TrialEntitlement, UUID> {
    @Modifying
    @Query(value = "INSERT INTO trial_entitlements(id,created_at,updated_at,student_id,teacher_id,consumed) "
            + "VALUES(gen_random_uuid(),now(),now(),:student,:teacher,false) ON CONFLICT(student_id,teacher_id) DO NOTHING", nativeQuery = true)
    int ensurePair(@Param("student") UUID studentId, @Param("teacher") UUID teacherId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<TrialEntitlement> findByStudentIdAndTeacherId(UUID studentId, UUID teacherId);

    @Query("select t from TrialEntitlement t where t.studentId=:student and t.teacherId=:teacher")
    Optional<TrialEntitlement> eligibility(@Param("student") UUID studentId, @Param("teacher") UUID teacherId);
}
