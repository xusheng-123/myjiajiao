package com.nurture.learning.infra;
import com.nurture.learning.domain.LearningFile;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.*;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface LearningFileRepository extends JpaRepository<LearningFile,UUID>,JpaSpecificationExecutor<LearningFile> {
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select f from LearningFile f where f.id=:id") Optional<LearningFile> locked(UUID id);
    long countByTaskIdAndDeletedFalse(UUID id);
    long countByQuestionIdAndDeletedFalse(UUID id);
    long countByBookingIdAndDeletedFalse(UUID id);
}
