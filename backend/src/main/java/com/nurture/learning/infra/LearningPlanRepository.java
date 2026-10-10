package com.nurture.learning.infra;
import com.nurture.learning.domain.LearningPlan;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.*;
import jakarta.persistence.LockModeType;
import java.util.*;

public interface LearningPlanRepository extends JpaRepository<LearningPlan,UUID> {
    Page<LearningPlan> findByStudentIdOrderByCreatedAtDesc(UUID studentId,Pageable page);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from LearningPlan p where p.id=:id")
    Optional<LearningPlan> locked(@Param("id") UUID id);
}
