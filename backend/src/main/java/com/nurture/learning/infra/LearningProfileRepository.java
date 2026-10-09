package com.nurture.learning.infra;

import com.nurture.learning.domain.LearningProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LearningProfileRepository extends JpaRepository<LearningProfile, UUID> {

    Optional<LearningProfile> findByStudentId(UUID studentId);
}
