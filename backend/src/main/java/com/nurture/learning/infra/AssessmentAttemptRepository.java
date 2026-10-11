package com.nurture.learning.infra;
import com.nurture.learning.domain.AssessmentAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface AssessmentAttemptRepository extends JpaRepository<AssessmentAttempt,UUID>,org.springframework.data.jpa.repository.JpaSpecificationExecutor<AssessmentAttempt>{Optional<AssessmentAttempt> findByAssessmentId(UUID assessmentId);}
