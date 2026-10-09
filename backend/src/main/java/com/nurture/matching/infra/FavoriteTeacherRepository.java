package com.nurture.matching.infra;

import com.nurture.matching.domain.FavoriteTeacher;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FavoriteTeacherRepository extends JpaRepository<FavoriteTeacher, UUID> {

    List<FavoriteTeacher> findByStudentIdOrderByCreatedAtDesc(UUID studentId);

    Optional<FavoriteTeacher> findByStudentIdAndTeacherId(UUID studentId, UUID teacherId);

    boolean existsByStudentIdAndTeacherId(UUID studentId, UUID teacherId);
}
