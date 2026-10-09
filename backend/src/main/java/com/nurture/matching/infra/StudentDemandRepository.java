package com.nurture.matching.infra;

import com.nurture.matching.domain.StudentDemand;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface StudentDemandRepository extends JpaRepository<StudentDemand, UUID> {

    Page<StudentDemand> findByStudentIdOrderByCreatedAtDesc(UUID studentId, Pageable pageable);

    Page<StudentDemand> findByStatusOrderByCreatedAtDesc(StudentDemand.DemandStatus status, Pageable pageable);
}
