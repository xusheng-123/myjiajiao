package com.nurture.learning.infra;
import com.nurture.learning.domain.LearningAssessment;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface LearningAssessmentRepository extends JpaRepository<LearningAssessment,UUID>,JpaSpecificationExecutor<LearningAssessment>{
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select q from LearningAssessment q where q.id=:id") Optional<LearningAssessment> locked(UUID id);
}
