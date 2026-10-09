package com.nurture.booking.infra;

import com.nurture.booking.domain.LessonReminder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface LessonReminderRepository extends JpaRepository<LessonReminder, UUID> {

    /** 提醒去重：同一预约同一档期仅提醒一次。 */
    boolean existsByBookingIdAndSlotId(UUID bookingId, UUID slotId);
}
