package com.nurture.booking.app;

import com.nurture.booking.app.BookingApplicationService;
import com.nurture.booking.domain.Booking;
import com.nurture.booking.infra.BookingRepository;
import com.nurture.messaging.app.MessagingApplicationService;
import com.nurture.order.app.OrderApplicationService;
import com.nurture.refund.app.RefundApplicationService;
import com.nurture.scheduling.app.SchedulingApplicationService;
import com.nurture.shared.security.CallerPrincipal;
import com.nurture.shared.security.CallerRole;
import com.nurture.shared.security.Scope;
import com.nurture.shared.twophase.TwoPhaseService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 预约核心状态机单元测试：开课/结课归属校验 + 改期（D11）原子性与通知。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BookingApplicationServiceTest {

    @Mock BookingRepository bookingRepository;
    @Mock SchedulingApplicationService schedulingService;
    @Mock OrderApplicationService orderService;
    @Mock RefundApplicationService refundService;
    @Mock TwoPhaseService twoPhaseService;
    @Mock MessagingApplicationService messagingService;
    @Mock com.nurture.booking.infra.TrialEntitlementRepository trialEntitlements;

    private BookingApplicationService service;
    private final UUID studentId = UUID.randomUUID();
    private final UUID teacherId = UUID.randomUUID();
    private final UUID oldSlotId = UUID.randomUUID();
    private final UUID newSlotId = UUID.randomUUID();
    private Booking confirmedBooking;

    @BeforeEach
    void setUp() {
        service = new BookingApplicationService(bookingRepository, schedulingService,
                orderService, refundService, twoPhaseService, messagingService, trialEntitlements);
        confirmedBooking = new Booking(studentId, teacherId, oldSlotId, "物理",
                Booking.BookingType.FORMAL, 30_000L);
        confirmedBooking.confirm();
        lenient().when(bookingRepository.findById(confirmedBooking.getId()))
                .thenReturn(Optional.of(confirmedBooking));
        // 改期时间校验（2026-10-10 决策）读取目标档期开始时间：默认桩为未来档期
        var futureSlot = new com.nurture.scheduling.domain.AvailabilitySlot(
                teacherId, java.time.OffsetDateTime.now().plusHours(48),
                java.time.OffsetDateTime.now().plusHours(49));
        lenient().when(schedulingService.holdSlotStartAt(newSlotId, teacherId)).thenReturn(futureSlot.getStartAt());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(UUID accountId, CallerRole role) {
        var principal = new CallerPrincipal(accountId, role, "测试用户", Set.of());
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(principal, null));
    }

    // ------------------------------------------------- 开课 / 结课归属校验

    @Test
    @DisplayName("归属导师 start/complete → 状态推进为 IN_PROGRESS → COMPLETED")
    void ownerTeacherCanStartAndComplete() {
        loginAs(teacherId, CallerRole.TEACHER);

        service.start(confirmedBooking.getId());
        assertThat(confirmedBooking.getStatus()).isEqualTo(Booking.BookingStatus.IN_PROGRESS);

        service.complete(confirmedBooking.getId());
        assertThat(confirmedBooking.getStatus()).isEqualTo(Booking.BookingStatus.COMPLETED);
    }

    @Test
    @DisplayName("非归属导师（含学生本人）start → 403 AccessDenied")
    void nonOwnerTeacherCannotStart() {
        loginAs(studentId, CallerRole.STUDENT);

        assertThatThrownBy(() -> service.start(confirmedBooking.getId()))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("归属导师");
        assertThat(confirmedBooking.getStatus()).isEqualTo(Booking.BookingStatus.CONFIRMED);
    }

    @Test
    @DisplayName("鉴权上下文缺失 → 拒绝操作")
    void missingAuthContextIsRejected() {
        assertThatThrownBy(() -> service.start(confirmedBooking.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("鉴权上下文缺失");
    }

    // ------------------------------------------------- 改期（D11）

    @Test
    @DisplayName("学生改期 → 先锁新档期再释放旧档期，并通知导师")
    void studentRescheduleHoldsNewSlotThenReleasesOld() {
        loginAs(studentId, CallerRole.STUDENT);

        Booking rescheduled = service.reschedule(confirmedBooking.getId(), newSlotId);

        assertThat(rescheduled.getSlotId()).isEqualTo(newSlotId);
        verify(schedulingService).holdSlotStartAt(newSlotId, teacherId);
        verify(schedulingService).releaseSlot(oldSlotId);
        verify(messagingService).notify(eq(teacherId), eq("RESCHEDULE"), any(), any());
    }

    @Test
    @DisplayName("导师改期 → 通知学生一方")
    void teacherRescheduleNotifiesStudent() {
        loginAs(teacherId, CallerRole.TEACHER);

        service.reschedule(confirmedBooking.getId(), newSlotId);

        verify(messagingService).notify(eq(studentId), eq("RESCHEDULE"), any(), any());
    }

    @Test
    @DisplayName("非参与双方且非运营 → 拒绝改期")
    void outsiderCannotReschedule() {
        UUID outsiderId = UUID.randomUUID();
        loginAs(outsiderId, CallerRole.PARENT);

        assertThatThrownBy(() -> service.reschedule(confirmedBooking.getId(), newSlotId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("仅预约参与双方");
        verify(schedulingService, never()).holdSlotStartAt(any(), any());
    }

    @Test
    @DisplayName("新档期与当前档期相同 → 拒绝")
    void sameSlotRescheduleRejected() {
        loginAs(studentId, CallerRole.STUDENT);

        assertThatThrownBy(() -> service.reschedule(confirmedBooking.getId(), oldSlotId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("相同");
    }

    @Test
    @DisplayName("目标档期已过去 → 拒绝且不释放旧档期（时间规则与新建预约一致）")
    void pastTargetSlotRescheduleRejected() {
        loginAs(studentId, CallerRole.STUDENT);
        var pastSlot = new com.nurture.scheduling.domain.AvailabilitySlot(
                teacherId, java.time.OffsetDateTime.now().minusHours(2),
                java.time.OffsetDateTime.now().minusHours(1));
        when(schedulingService.holdSlotStartAt(newSlotId, teacherId)).thenReturn(pastSlot.getStartAt());

        assertThatThrownBy(() -> service.reschedule(confirmedBooking.getId(), newSlotId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("已过去");
        // 事务语义：失败请求不得释放旧档期
        verify(schedulingService, never()).releaseSlot(any());
        assertThat(confirmedBooking.getSlotId()).isEqualTo(oldSlotId);
    }

    @Test
    @DisplayName("未确认预约不可改期（未确认走审批流）")
    void unconfirmedBookingCannotReschedule() {
        Booking requested = new Booking(studentId, teacherId, oldSlotId, "物理",
                Booking.BookingType.FORMAL, 30_000L); // REQUESTED
        when(bookingRepository.findById(requested.getId())).thenReturn(Optional.of(requested));
        loginAs(studentId, CallerRole.STUDENT);

        assertThatThrownBy(() -> service.reschedule(requested.getId(), newSlotId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("仅已确认");
        verify(schedulingService, never()).holdSlotStartAt(any(), any());
    }

    @Test
    @DisplayName("预约不存在 → IllegalArgumentException")
    void unknownBookingRejected() {
        loginAs(studentId, CallerRole.STUDENT);

        assertThatThrownBy(() -> service.reschedule(UUID.randomUUID(), newSlotId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("预约不存在");
    }
}
