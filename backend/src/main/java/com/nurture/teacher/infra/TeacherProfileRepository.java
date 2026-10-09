package com.nurture.teacher.infra;

import com.nurture.teacher.domain.TeacherProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TeacherProfileRepository extends JpaRepository<TeacherProfile, UUID> {

    Optional<TeacherProfile> findByAccountId(UUID accountId);
}
