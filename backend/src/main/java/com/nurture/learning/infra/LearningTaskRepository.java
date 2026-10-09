package com.nurture.learning.infra;

import com.nurture.learning.domain.LearningTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LearningTaskRepository extends JpaRepository<LearningTask, UUID> {

    List<LearningTask> findByStudentIdOrderByCreatedAtDesc(UUID studentId);

    List<LearningTask> findByTeacherIdOrderByCreatedAtDesc(UUID teacherId);

    List<LearningTask> findByStudentIdAndStatus(UUID studentId, LearningTask.Status status);
}
