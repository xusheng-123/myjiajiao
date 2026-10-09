package com.nurture.classroom.infra;

import com.nurture.classroom.domain.ClassroomSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ClassroomSessionRepository extends JpaRepository<ClassroomSession, UUID> {

    Optional<ClassroomSession> findByBookingId(UUID bookingId);
}
