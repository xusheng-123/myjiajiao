package com.nurture.student.infra;

import com.nurture.student.domain.StudentProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface StudentProfileRepository extends JpaRepository<StudentProfile, UUID> {

    Optional<StudentProfile> findByAccountId(UUID accountId);

    Optional<StudentProfile> findByStudentNo(String studentNo);
}
