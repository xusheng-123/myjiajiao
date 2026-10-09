package com.nurture.booking.app;

import com.nurture.booking.app.LessonReminderScheduler;
import com.nurture.booking.domain.Booking;
import com.nurture.booking.domain.LessonReminder;
import com.nurture.booking.infra.BookingRepository;
import com.nurture.booking.infra.LessonReminderRepository;
import com.nurture.messaging.app.MessagingApplicationService;
import com.nurture.scheduling.app.SchedulingApplicationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 课程临近提醒（D12）单元测试：窗口判定 / 双方触达 / 幂等去重。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LessonReminderSchedulerTest {

    @Mock BookingRepository bookingRepository;
    @Mock SchedulingApplicationService schedulingService;
    @Mock LessonReminderRepository reminderRepository;
    @Mock MessagingApplicationService messagingService;

    private LessonReminderScheduler scheduler;
    private final UUID studentId = UUID.randomUUID();
    private final UUID teacherId = UUID.randomUUID();
    private final UUID slotId = UUID.randomUUID();
    private Booking confirmedBooking;

    @BeforeEach
    void setUp() {
        scheduler = new LessonReminderScheduler(bookingRepository, schedulingService,
                reminderRepository, messagingService);
        confirmedBooking = new Booking(studentId, teacherId, slotId, "数学",
                Booking.BookingType.TRIAL, 0);
        confirmedBooking.confirm();
        lenient().when(bookingRepository.findByStatus(Booking.BookingStatus.CONFIRMED))
                .thenReturn(List.of(confirmedBooking));
        lenient().when(reminderRepository.existsByBookingIdAndSlotId(confirmedBooking.getId(), slotId))
                .thenReturn(false);
        lenient().when(reminderRepository.save(any(LessonReminder.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private void slotStartingAt(OffsetDateTime startAt) {
        lenient().when(schedulingService.slotOf(slotId)).thenReturn(Optional.of(
                new SchedulingApplicationService.SlotView(slotId, teacherId, startAt,
                        startAt.plusMinutes(60), "BOOKED")));
    }

    @Test
    @DisplayName("开课前 30 分钟窗口内的已确认预约 → 师生双方各收到一条提醒且落去重记录")
    void sendsReminderToBothPartiesWithinWindow() {
        slotStartingAt(OffsetDateTime.now().plusMinutes(20));

        scheduler.scanUpcomingLessons();

        verify(messagingService).notify(eq(studentId), eq("LESSON_REMINDER"), any(), any());
        verify(messagingService).notify(eq(teacherId), eq("LESSON_REMINDER"), any(), any());
        verify(reminderRepository).save(any(LessonReminder.class));
    }

    @Test
    @DisplayName("开课时间超出 30 分钟窗口 → 不发送提醒")
    void skipsSlotOutsideWindow() {
        slotStartingAt(OffsetDateTime.now().plusMinutes(90));

        scheduler.scanUpcomingLessons();

        verify(messagingService, never()).notify(any(), any(), any(), any());
        verify(reminderRepository, never()).save(any());
    }

    @Test
    @DisplayName("同一预约同一档期已提醒过 → 幂等跳过，不重复触达")
    void skipsAlreadyRemindedBooking() {
        slotStartingAt(OffsetDateTime.now().plusMinutes(10));
        when(reminderRepository.existsByBookingIdAndSlotId(confirmedBooking.getId(), slotId))
                .thenReturn(true);

        scheduler.scanUpcomingLessons();

        verify(messagingService, never()).notify(any(), any(), any(), any());
        verify(reminderRepository, never()).save(any());
    }

    @Test
    @DisplayName("档期已被删除（slotOf 为空）→ 跳过且不报错")
    void skipsMissingSlot() {
        when(schedulingService.slotOf(slotId)).thenReturn(Optional.empty());

        scheduler.scanUpcomingLessons();

        verify(messagingService, never()).notify(any(), any(), any(), any());
    }
}
