package com.nurture.learning.infra;

import com.nurture.learning.domain.Question;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QuestionRepository extends JpaRepository<Question, UUID> {

    List<Question> findByStudentIdOrderByCreatedAtDesc(UUID studentId);

    List<Question> findByTeacherIdOrderByCreatedAtDesc(UUID teacherId);

    List<Question> findByTeacherIdAndStatusOrderByCreatedAtDesc(UUID teacherId, Question.Status status);
}
