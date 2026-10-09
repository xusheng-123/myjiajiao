package com.nurture.classroom.infra;

import com.nurture.classroom.domain.PreparationTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PreparationTaskRepository extends JpaRepository<PreparationTask, UUID> {

    List<PreparationTask> findByBookingIdOrderBySortIndexAsc(UUID bookingId);
}
