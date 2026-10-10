package com.nurture.learning.infra;

import com.nurture.learning.domain.LearningTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LearningTaskRepository extends JpaRepository<LearningTask, UUID>,org.springframework.data.jpa.repository.JpaSpecificationExecutor<LearningTask> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select t from LearningTask t where t.id=:id")
    java.util.Optional<LearningTask> locked(UUID id);


    List<LearningTask> findByStudentIdOrderByCreatedAtDesc(UUID studentId);

    List<LearningTask> findByTeacherIdOrderByCreatedAtDesc(UUID teacherId);

    List<LearningTask> findByStudentIdAndStatus(UUID studentId, LearningTask.Status status);
}
