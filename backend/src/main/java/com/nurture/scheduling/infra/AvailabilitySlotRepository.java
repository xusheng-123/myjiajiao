package com.nurture.scheduling.infra;

import com.nurture.scheduling.domain.AvailabilitySlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface AvailabilitySlotRepository extends JpaRepository<AvailabilitySlot, UUID> {

    List<AvailabilitySlot> findByTeacherIdAndStatusOrderByStartAtAsc(UUID teacherId, AvailabilitySlot.SlotStatus status);

    /** 重叠检测：start < existingEnd AND end > existingStart。 */
    @Query("""
            SELECT s FROM AvailabilitySlot s
            WHERE s.teacherId = :teacherId
              AND s.startAt < :endAt AND s.endAt > :startAt
            """)
    List<AvailabilitySlot> findOverlapping(@Param("teacherId") UUID teacherId,
                                           @Param("startAt") OffsetDateTime startAt,
                                           @Param("endAt") OffsetDateTime endAt);

    List<AvailabilitySlot> findByTeacherIdAndStartAtBetweenOrderByStartAtAsc(UUID teacherId,
                                                                             OffsetDateTime from, OffsetDateTime to);
}
