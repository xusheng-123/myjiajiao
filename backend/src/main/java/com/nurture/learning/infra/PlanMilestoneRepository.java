package com.nurture.learning.infra;
import com.nurture.learning.domain.PlanMilestone;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface PlanMilestoneRepository extends JpaRepository<PlanMilestone,UUID> {
    List<PlanMilestone> findByPlanIdOrderByCreatedAtAsc(UUID planId);
    long countByPlanId(UUID planId);
}
