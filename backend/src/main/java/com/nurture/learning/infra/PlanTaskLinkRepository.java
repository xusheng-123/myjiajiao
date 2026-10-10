package com.nurture.learning.infra;
import com.nurture.learning.domain.PlanTaskLink;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface PlanTaskLinkRepository extends JpaRepository<PlanTaskLink,UUID> {
    List<PlanTaskLink> findByPlanIdOrderByCreatedAtAsc(UUID planId);
    Optional<PlanTaskLink> findByPlanIdAndTaskId(UUID planId,UUID taskId);
    long countByPlanId(UUID planId);
}
