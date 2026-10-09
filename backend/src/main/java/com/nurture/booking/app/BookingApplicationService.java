package com.nurture.booking.app;

import com.nurture.booking.domain.Booking;
import com.nurture.booking.infra.BookingRepository;
import com.nurture.messaging.app.MessagingApplicationService;
import com.nurture.order.app.OrderApplicationService;
import com.nurture.order.domain.Order;
import com.nurture.refund.app.RefundApplicationService;
import com.nurture.scheduling.app.SchedulingApplicationService;
import com.nurture.shared.security.CallerContext;
import com.nurture.shared.twophase.TwoPhaseService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 预约管理应用服务 — 试听/正式课预约、审批确认与两阶段取消（Preview/Execute）。
 */
@Service
public class BookingApplicationService {

    private final BookingRepository bookingRepository;
    private final SchedulingApplicationService schedulingService;
    private final OrderApplicationService orderService;
    private final RefundApplicationService refundService;
    private final TwoPhaseService twoPhaseService;
    private final MessagingApplicationService messagingService;

    public BookingApplicationService(BookingRepository bookingRepository,
                                     SchedulingApplicationService schedulingService,
                                     OrderApplicationService orderService,
                                     RefundApplicationService refundService,
                                     TwoPhaseService twoPhaseService,
                                     MessagingApplicationService messagingService) {
        this.bookingRepository = bookingRepository;
        this.schedulingService = schedulingService;
        this.orderService = orderService;
        this.refundService = refundService;
        this.twoPhaseService = twoPhaseService;
        this.messagingService = messagingService;
    }

    @Transactional
    public Booking create(UUID studentId, UUID teacherId, UUID slotId, String subject, Booking.BookingType type) {
        // 并发冲突排他：同一时段仅一个活跃预约（数据库部分唯一索引兜底）
        var slot = schedulingService.holdSlot(slotId, teacherId);
        // 价格在订单中心创建时按导师定价快照；预约侧记 0，试听课免费
        Booking booking = new Booking(studentId, teacherId, slotId, subject, type, 0);
        Booking saved = bookingRepository.save(booking);
        if (type == Booking.BookingType.TRIAL) {
            saved.confirm(); // 试听课直接确认，课时费为 0
        }
        return saved;
    }

    /** 审批决定视图（teacher.api 返回用）。 */
    public record BookingDecisionView(UUID bookingId, String status) {
    }

    @Transactional
    public BookingDecisionView approveAsTeacher(UUID bookingId) {
        Booking booking = requireOwnedBooking(bookingId, CallerContext.require().accountId());
        booking.confirm();
        schedulingService.bookSlot(booking.getSlotId());
        return new BookingDecisionView(booking.getId(), booking.getStatus().name());
    }

    /** 导师审批驳回：属于审批流而非自助取消，直接释放档期。 */
    @Transactional
    public BookingDecisionView rejectAsTeacher(UUID bookingId) {
        Booking booking = requireOwnedBooking(bookingId, CallerContext.require().accountId());
        booking.cancel("导师审批未通过，档期已释放");
        schedulingService.releaseSlot(booking.getSlotId());
        orderService.closeIfPresent(bookingId);
        return new BookingDecisionView(booking.getId(), booking.getStatus().name());
    }

    /** 开始上课：仅预约归属导师可操作（防止任意用户篡改课程状态）。 */
    @Transactional
    public Booking start(UUID bookingId) {
        Booking booking = requireOwnedBooking(bookingId, CallerContext.require().accountId());
        booking.start();
        return booking;
    }

    /** 完成课程：仅预约归属导师可操作。 */
    @Transactional
    public Booking complete(UUID bookingId) {
        Booking booking = requireOwnedBooking(bookingId, CallerContext.require().accountId());
        booking.complete();
        return booking;
    }

    // -------------------------------------------------- 两阶段取消：Preview

    // -------------------------------------------------- 改期（D11）

    /**
     * 改期：仅参与双方（或运营）可操作；仅已确认预约支持；
     * 新档期必须属于同一导师且为可锁定状态。先锁新档期再释放旧档期，
     * 冲突时事务原子回滚、原档期保留（前端可提示重新选择时间）。
     * 订单保持不变：已支付订单对改期后的课程仍然有效。
     */
    @Transactional
    public Booking reschedule(UUID bookingId, UUID newSlotId) {
        var caller = CallerContext.require();
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("预约不存在"));
        boolean participant = booking.getStudentId().equals(caller.accountId())
                || booking.getTeacherId().equals(caller.accountId());
        if (!participant && caller.role() != com.nurture.shared.security.CallerRole.ADMIN) {
            throw new AccessDeniedException("仅预约参与双方或运营可改期");
        }
        if (booking.getStatus() != Booking.BookingStatus.CONFIRMED) {
            throw new IllegalStateException("仅已确认的预约支持改期；未确认预约请走审批流，已开课预约不可改期");
        }
        if (booking.getSlotId().equals(newSlotId)) {
            throw new IllegalStateException("新档期与当前档期相同，请选择其他时间");
        }
        // 先锁新档期（占用/冲突校验），成功后再释放旧档期：任一步失败整体回滚
        schedulingService.holdSlot(newSlotId, booking.getTeacherId());
        schedulingService.releaseSlot(booking.getSlotId());
        booking.rescheduleTo(newSlotId);
        UUID counterpart = caller.accountId().equals(booking.getStudentId())
                ? booking.getTeacherId() : booking.getStudentId();
        messagingService.notify(counterpart, "RESCHEDULE", "课程已改期",
                "你的一节课程已改期，请以最新档期时间为准；如时间冲突请联系对方协调。");
        return booking;
    }

    // -------------------------------------------------- 两阶段取消：Preview

    /** 注意：Preview 会落库确认凭证（confirmation_grants），严禁 readOnly 事务。 */
    @Transactional
    public CancelPreview previewCancel(UUID bookingId, String reason) {
        Booking booking = requireCancellableByCaller(bookingId);
        Order order = orderRepositorySafe(bookingId);

        long refundCents = 0;
        String orderStatus = "NO_ORDER";
        if (order != null) {
            orderStatus = order.getStatus().name();
            refundCents = order.getStatus() == Order.OrderStatus.PAID ? order.getAmountCents() : 0;
        }

        Map<String, Object> impact = new LinkedHashMap<>();
        impact.put("bookingId", bookingId);
        impact.put("cancelReason", reason);
        impact.put("willReleaseSlot", true);
        impact.put("refundAmountCents", refundCents);
        impact.put("refundDescription", refundCents > 0
                ? "订单已支付，取消后全额原路退回家庭钱包" : "该预约无已付订单或订单尚未支付，取消不产生退款");
        impact.put("affectedLessons", refundCents > 0 ? 1 : 0);
        impact.put("orderStatus", orderStatus);

        var grant = twoPhaseService.issue("booking:cancel", bookingId, impact.toString());
        return new CancelPreview(impact, grant.confirmationToken(), grant.expiresAt());
    }

    // ------------------------------------------------- 两阶段取消：Execute

    @Transactional
    public Booking executeCancel(UUID bookingId, String reason, String confirmationToken) {
        twoPhaseService.consume("booking:cancel", bookingId, confirmationToken);
        Booking booking = requireCancellableByCaller(bookingId);
        booking.cancel(reason);
        schedulingService.releaseSlot(booking.getSlotId());

        Order order = orderRepositorySafe(bookingId);
        if (order != null && order.getStatus() == Order.OrderStatus.PAID) {
            // 已付订单：取消即触发退款域自动全额退款闭环（与 Preview 展示的影响一致）
            refundService.executeForBookingCancellation(order, reason);
        }
        orderService.closeIfPresent(bookingId);
        return booking;
    }

    // -------------------------------------------------------------- 查询

    @Transactional(readOnly = true)
    public Page<Booking> requestsForTeacher(UUID teacherId, Pageable pageable) {
        return bookingRepository.findByTeacherIdOrderByCreatedAtDesc(teacherId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Booking> ofStudent(UUID studentId, Pageable pageable) {
        return bookingRepository.findByStudentIdOrderByCreatedAtDesc(studentId, pageable);
    }

    /** 教师工作台「当日课程」：今天有课的已确认/进行中预约，按开始时间排序。 */
    @Transactional(readOnly = true)
    public List<TodayLesson> todayLessonsForTeacher(UUID teacherId) {
        OffsetDateTime dayStart = OffsetDateTime.now().toLocalDate().atStartOfDay().atOffset(OffsetDateTime.now().getOffset());
        OffsetDateTime dayEnd = dayStart.plusDays(1);
        return bookingRepository.findByTeacherIdOrderByCreatedAtDesc(teacherId, PageRequest.of(0, 100)).getContent()
                .stream()
                .filter(b -> b.getStatus() == Booking.BookingStatus.CONFIRMED
                        || b.getStatus() == Booking.BookingStatus.IN_PROGRESS)
                .flatMap(b -> schedulingService.slotOf(b.getSlotId()).stream()
                        .filter(slot -> !slot.startAt().isBefore(dayStart) && slot.startAt().isBefore(dayEnd))
                        .map(slot -> new TodayLesson(b.getId(), b.getSubject(), slot.startAt(), slot.endAt(),
                                b.getStudentId(), b.getStatus().name())))
                .sorted(java.util.Comparator.comparing(TodayLesson::startAt))
                .toList();
    }

    @Transactional(readOnly = true)
    public Booking get(UUID bookingId) {
        return bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("预约不存在"));
    }

    // ------------------------------------ 供其他模块经 app 层消费的查询（架构收口）

    /** 参与方校验（classroom/review 等模块消费）。 */
    @Transactional(readOnly = true)
    public boolean isParticipant(UUID bookingId, UUID accountId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("预约不存在"));
        return booking.getStudentId().equals(accountId) || booking.getTeacherId().equals(accountId);
    }

    /** 预约双方与状态（review 双向评价方向判定消费）。 */
    public record BookingRoles(UUID studentId, UUID teacherId, String status) {
    }

    @Transactional(readOnly = true)
    public BookingRoles rolesOf(UUID bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("预约不存在"));
        return new BookingRoles(booking.getStudentId(), booking.getTeacherId(), booking.getStatus().name());
    }

    /** 订单视图（order 模块下单前校验消费；Controller 经 booking.app 获取后传入 order.app）。 */
    public record BookingOrderView(UUID bookingId, UUID studentId, UUID teacherId,
                                   String subject, String status) {
    }

    @Transactional(readOnly = true)
    public BookingOrderView orderViewOf(UUID bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("预约不存在"));
        return new BookingOrderView(booking.getId(), booking.getStudentId(), booking.getTeacherId(),
                booking.getSubject(), booking.getStatus().name());
    }

    /** 学生 upcoming 课程视图（student 控制台课程表与专注模式消费）。 */
    public record UpcomingBookingView(UUID bookingId, String subject, String status,
                                      OffsetDateTime startAt, OffsetDateTime endAt, UUID teacherId) {
    }

    @Transactional(readOnly = true)
    public List<UpcomingBookingView> upcomingForStudent(UUID studentId) {
        return bookingRepository.findByStudentIdOrderByCreatedAtDesc(studentId,
                        org.springframework.data.domain.PageRequest.of(0, 20)).getContent().stream()
                .filter(b -> b.getStatus() == Booking.BookingStatus.REQUESTED
                        || b.getStatus() == Booking.BookingStatus.CONFIRMED
                        || b.getStatus() == Booking.BookingStatus.IN_PROGRESS)
                .map(b -> {
                    var slot = schedulingService.slotOf(b.getSlotId()).orElse(null);
                    return new UpcomingBookingView(b.getId(), b.getSubject(), b.getStatus().name(),
                            slot == null ? null : slot.startAt(), slot == null ? null : slot.endAt(),
                            b.getTeacherId());
                }).toList();
    }

    /** 学生历史课程（已完成/已结课）：课程表「评价」入口的数据源，复用同一视图。 */
    @Transactional(readOnly = true)
    public List<UpcomingBookingView> historyForStudent(UUID studentId) {
        return bookingRepository.findByStudentIdOrderByCreatedAtDesc(studentId,
                        org.springframework.data.domain.PageRequest.of(0, 20)).getContent().stream()
                .filter(b -> b.getStatus() == Booking.BookingStatus.COMPLETED
                        || b.getStatus() == Booking.BookingStatus.CLOSED)
                .map(b -> {
                    var slot = schedulingService.slotOf(b.getSlotId()).orElse(null);
                    return new UpcomingBookingView(b.getId(), b.getSubject(), b.getStatus().name(),
                            slot == null ? null : slot.startAt(), slot == null ? null : slot.endAt(),
                            b.getTeacherId());
                }).toList();
    }

    /** 学生已确认课程：家长「可下单课程」视图的数据源（订单侧再做去重与定价快照）。 */
    @Transactional(readOnly = true)
    public List<UpcomingBookingView> confirmedForStudent(UUID studentId) {
        return bookingRepository.findByStudentIdOrderByCreatedAtDesc(studentId,
                        org.springframework.data.domain.PageRequest.of(0, 20)).getContent().stream()
                .filter(b -> b.getStatus() == Booking.BookingStatus.CONFIRMED)
                .map(b -> {
                    var slot = schedulingService.slotOf(b.getSlotId()).orElse(null);
                    return new UpcomingBookingView(b.getId(), b.getSubject(), b.getStatus().name(),
                            slot == null ? null : slot.startAt(), slot == null ? null : slot.endAt(),
                            b.getTeacherId());
                }).toList();
    }

    @Transactional(readOnly = true)
    public long countAll() {
        return bookingRepository.count();
    }

    // ------------------------------------------------------------ 内部工具

    private Booking requireOwnedBooking(UUID bookingId, UUID teacherId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("预约不存在"));
        if (!booking.getTeacherId().equals(teacherId)) {
            throw new AccessDeniedException("仅预约归属导师可审批该预约");
        }
        return booking;
    }

    private Booking requireCancellableByCaller(UUID bookingId) {
        var caller = CallerContext.require();
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("预约不存在"));
        boolean participant = booking.getStudentId().equals(caller.accountId())
                || booking.getTeacherId().equals(caller.accountId());
        if (!participant && caller.role() != com.nurture.shared.security.CallerRole.ADMIN) {
            throw new AccessDeniedException("仅预约参与双方或运营方可取消预约");
        }
        if (!booking.cancellable()) {
            throw new IllegalStateException("当前预约状态不可取消: " + booking.getStatus());
        }
        return booking;
    }

    private Order orderRepositorySafe(UUID bookingId) {
        try {
            return orderService.getByBooking(bookingId);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public record CancelPreview(Map<String, Object> cascadeImpact, String confirmationToken,
                                java.time.Instant confirmationTokenExpiresAt) {
    }

    public record TodayLesson(UUID bookingId, String subject, OffsetDateTime startAt, OffsetDateTime endAt,
                              UUID studentId, String status) {
    }

    /** 学习成长：校验教师对该课程的归属，返回学生 ID（供任务 / 课堂记录落学生维度）。 */
    @Transactional(readOnly = true)
    public UUID studentOfOwnedBooking(UUID bookingId, UUID teacherId) {
        Booking b = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("预约不存在"));
        if (!b.getTeacherId().equals(teacherId)) {
            throw new AccessDeniedException("仅课程归属教师可操作");
        }
        return b.getStudentId();
    }

    /** 学习成长：教师与该学生之间是否存在授课关系（历史预约即可）。 */
    @Transactional(readOnly = true)
    public boolean hasTeachingRelation(UUID teacherId, UUID studentId) {
        return bookingRepository.existsByTeacherIdAndStudentId(teacherId, studentId);
    }

    /** 学习成长：教师授课过的学生（去重，含任意状态）。 */
    @Transactional(readOnly = true)
    public java.util.List<UUID> studentsOfTeacher(UUID teacherId) {
        return bookingRepository.findByTeacherId(teacherId).stream()
                .map(b -> b.getStudentId())
                .distinct()
                .toList();
    }

    /** 学习成长：教师与某学生的历史预约（供授权档案查看）。 */
    @Transactional(readOnly = true)
    public java.util.List<Booking> historyBetween(UUID teacherId, UUID studentId) {
        return bookingRepository.findByTeacherIdAndStudentId(teacherId, studentId);
    }
}
