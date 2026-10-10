package com.nurture.scheduling.infra;

import com.nurture.scheduling.domain.AvailabilitySlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface AvailabilitySlotRepository extends JpaRepository<AvailabilitySlot, UUID> {

    /** 可约档期 = OPEN 且尚未开始（质量加固：过期 OPEN 档期不作为有效可约时间）。 */
    List<AvailabilitySlot> findByTeacherIdAndStatusAndStartAtAfterOrderByStartAtAsc(
            UUID teacherId, AvailabilitySlot.SlotStatus status, OffsetDateTime now);

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
