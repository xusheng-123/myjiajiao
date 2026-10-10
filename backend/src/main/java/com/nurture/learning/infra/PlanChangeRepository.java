package com.nurture.learning.infra;
import com.nurture.learning.domain.PlanChange;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.*;
import java.util.UUID;
public interface PlanChangeRepository extends JpaRepository<PlanChange,UUID> {
    Page<PlanChange> findByPlanIdOrderByRevisionDesc(UUID planId,Pageable page);
}
