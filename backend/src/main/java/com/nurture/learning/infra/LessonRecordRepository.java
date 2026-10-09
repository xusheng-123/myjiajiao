package com.nurture.learning.infra;

import com.nurture.learning.domain.LessonRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LessonRecordRepository extends JpaRepository<LessonRecord, UUID> {

    Optional<LessonRecord> findByBookingId(UUID bookingId);

    List<LessonRecord> findByStudentIdOrderByCreatedAtDesc(UUID studentId);

    List<LessonRecord> findByTeacherIdOrderByCreatedAtDesc(UUID teacherId);

    List<LessonRecord> findByTeacherIdAndStudentIdOrderByCreatedAtDesc(UUID teacherId, UUID studentId);
}
