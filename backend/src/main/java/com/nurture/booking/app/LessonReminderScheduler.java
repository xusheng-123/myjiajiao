package com.nurture.booking.app;

import com.nurture.booking.domain.Booking;
import com.nurture.booking.domain.LessonReminder;
import com.nurture.booking.infra.BookingRepository;
import com.nurture.booking.infra.LessonReminderRepository;
import com.nurture.messaging.app.MessagingApplicationService;
import com.nurture.scheduling.app.SchedulingApplicationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * 课程临近提醒（D12 最小闭环）——复用现有机制，不引入新中间件：
 * <ul>
 *   <li>调度：Spring @Scheduled 定时扫描（应用已启用 @EnableScheduling）；</li>
 *   <li>触达：站内通知（messaging 域）推送给师生双方，文案含明确时间与下一步动作；</li>
 *   <li>可查看：通知出现在既有通知中心；</li>
 *   <li>已处理：通知已读状态（read_at）+ 唯一约束保证同一课不重复提醒。</li>
 * </ul>
 * 扫描窗口：开课前 30 分钟内的已确认预约；扫描间隔默认 60 秒，可用配置调整。
 */
@Component
public class LessonReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(LessonReminderScheduler.class);
    /** 提前提醒窗口：开课前 30 分钟。 */
    static final Duration REMIND_WINDOW = Duration.ofMinutes(30);

    private final BookingRepository bookingRepository;
    private final SchedulingApplicationService schedulingService;
    private final LessonReminderRepository reminderRepository;
    private final MessagingApplicationService messagingService;

    public LessonReminderScheduler(BookingRepository bookingRepository,
                                   SchedulingApplicationService schedulingService,
                                   LessonReminderRepository reminderRepository,
                                   MessagingApplicationService messagingService) {
        this.bookingRepository = bookingRepository;
        this.schedulingService = schedulingService;
        this.reminderRepository = reminderRepository;
        this.messagingService = messagingService;
    }

    @Scheduled(fixedDelayString = "${nurture.reminder.scan-interval-ms:60000}")
    public void scanUpcomingLessons() {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime horizon = now.plus(REMIND_WINDOW);
        int sent = 0;
        for (Booking booking : bookingRepository.findByStatus(Booking.BookingStatus.CONFIRMED)) {
            var slotView = schedulingService.slotOf(booking.getSlotId());
            if (slotView.isEmpty() || slotView.get().startAt().isBefore(now) || slotView.get().startAt().isAfter(horizon)) {
                continue;
            }
            if (reminderRepository.existsByBookingIdAndSlotId(booking.getId(), booking.getSlotId())) {
                continue; // 已提醒：唯一约束 + 存在性检查双重去重
            }
            OffsetDateTime lessonStart = slotView.get().startAt();
            long minutesLeft = Math.max(1, Duration.between(now, lessonStart).toMinutes());
            String timeText = lessonStart.toLocalTime().withSecond(0).toString()
                    + " 开始（约 " + minutesLeft + " 分钟后）";
            String title = "课程即将开始";
            String body = "「" + booking.getSubject() + "」将于 " + timeText + " 上课。"
                    + "临近开课请进入课堂，课前任务可按自己的节奏完成。";
            messagingService.notify(booking.getStudentId(), "LESSON_REMINDER", title, body);
            messagingService.notify(booking.getTeacherId(), "LESSON_REMINDER", title, body);
            reminderRepository.save(new LessonReminder(booking.getId(), booking.getSlotId(),
                    lessonStart));
            sent++;
        }
        if (sent > 0) {
            log.info("[LessonReminder] 本轮发送课程临近提醒 {} 条（窗口 {} 分钟）", sent, REMIND_WINDOW.toMinutes());
        }
    }
}
