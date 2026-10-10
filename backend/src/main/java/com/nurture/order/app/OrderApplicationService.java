package com.nurture.order.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nurture.booking.app.BookingApplicationService;
import com.nurture.order.domain.Order;
import com.nurture.order.infra.OrderRepository;
import com.nurture.parent.app.ParentApplicationService;
import com.nurture.shared.outbox.OutboxWriter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.nurture.shared.security.CallerContext;
import com.nurture.shared.security.CallerRole;
import com.nurture.teacher.app.TeacherApplicationService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 订单中心应用服务 — 待付 → 已付 → 已关闭 状态机与对账快照。
 * 下单资格/监护绑定校验/定价快照全部收敛在本服务（D1 + 架构收口），
 * Controller 仅做 Request → 本服务 → Response 的转发与跨模块视图编排。
 */
@Service
public class OrderApplicationService {

    private static final SecureRandom RANDOM = new SecureRandom();

    /** 导师未设置定价时的兜底课时费（300 元/课时）。 */
    private static final long DEFAULT_LESSON_PRICE_CENTS = 30_000L;

    private final OrderRepository orderRepository;
    private final OutboxWriter outboxWriter;
    private final ObjectMapper objectMapper;
    private final ParentApplicationService parentService;
    private final TeacherApplicationService teacherService;

    public OrderApplicationService(OrderRepository orderRepository, OutboxWriter outboxWriter,
                                   ObjectMapper objectMapper,
                                   ParentApplicationService parentService,
                                   TeacherApplicationService teacherService) {
        this.orderRepository = orderRepository;
        this.outboxWriter = outboxWriter;
        this.objectMapper = objectMapper;
        this.parentService = parentService;
        this.teacherService = teacherService;
    }

    public record OrderView(UUID orderId, String orderNo, UUID bookingId, String type, String status,
                            long amountCents, String snapshot, String paidAt) {
        static OrderView from(Order o) {
            return new OrderView(o.getId(), o.getOrderNo(), o.getBookingId(), o.getType().name(),
                    o.getStatus().name(), o.getAmountCents(), o.getReconciliationSnapshot(),
                    o.getPaidAt() == null ? null : o.getPaidAt().toString());
        }
    }

    /**
     * 下单（D1 + 架构收口）：资格判断、预约校验、监护绑定校验、定价快照与订单创建
     * 全部收敛在本服务。预约视图由调用方（Controller/Agent）经 booking.app 获取后传入，
     * 避免本服务反向依赖 booking 模块。
     */
    @Transactional
    public OrderView createForCaller(BookingApplicationService.BookingOrderView booking) {
        var caller = CallerContext.require();
        if (!"CONFIRMED".equals(booking.status())) {
            throw new IllegalStateException("仅已确认（导师已批准）的预约可以下单");
        }
        if (caller.role() != CallerRole.STUDENT && caller.role() != CallerRole.PARENT) {
            throw new AccessDeniedException("仅学生/家长可发起下单");
        }
        // D1：学生本人下单，或家长且与学生存在已确认监护绑定
        boolean canOrder = caller.role() == CallerRole.PARENT
                ? parentService.isActiveGuardian(caller.accountId(), booking.studentId())
                : caller.accountId().equals(booking.studentId());
        if (!canOrder) {
            throw new AccessDeniedException("仅预约学生本人或其已确认监护家长可下单");
        }
        orderRepository.findByBookingId(booking.bookingId()).ifPresent(o -> {
            throw new IllegalStateException("该预约已存在订单，严禁重复下单");
        });
        long priceCents = resolvePriceCents(booking.teacherId(), booking.subject());
        Order saved = create(booking.bookingId(), caller.accountId(), booking.studentId(),
                booking.teacherId(), Order.OrderType.FORMAL_LESSONS, priceCents, booking.subject(), null);
        return OrderView.from(saved);
    }

    /** 由支付回调驱动；联动结算与通知。 */
    @Transactional
    public Order markPaid(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("订单不存在"));
        order.markPaid();
        outboxWriter.write("order.paid", "Order", order.getId(),
                Map.of("orderNo", order.getOrderNo(),
                        "teacherId", order.getTeacherId().toString(),
                        "payerParentId", order.getPayerParentId().toString(),
                        "studentId", order.getStudentId().toString(),
                        "amountCents", order.getAmountCents()));
        return order;
    }

    @Transactional
    public Order cancel(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("订单不存在"));
        order.cancel();
        return order;
    }

    @Transactional
    public void closeIfPresent(UUID bookingId) {
        orderRepository.findByBookingId(bookingId).ifPresent(order -> {
            if (order.getStatus() == Order.OrderStatus.PENDING_PAYMENT) {
                order.cancel();
            } else if (order.getStatus() == Order.OrderStatus.PAID) {
                order.close();
            }
        });
    }

    /** noRollbackFor：预约无订单属合法业务态（试听课），不得把外层取消事务标记为 rollback-only。 */
    @Transactional(readOnly = true, noRollbackFor = IllegalArgumentException.class)
    public Order getByBooking(UUID bookingId) {
        return orderRepository.findByBookingId(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("该预约尚无关联订单"));
    }

    @Transactional(readOnly = true)
    public Page<OrderView> ordersOfPayerView(UUID payerParentId, Pageable pageable) {
        return ordersOfPayer(payerParentId, pageable).map(OrderView::from);
    }

    /** 家长「可下单课程」视图：已确认预约 − 已有订单，金额为服务端定价快照。 */
    public record OrderableLessonView(UUID bookingId, String subject, String startAt, long priceCents) {
    }

    /**
     * 孩子的可下单课程（家长/运营视角）。预约候选由 Controller 经 booking.app 获取后传入
     * （沿用本服务不反向依赖 booking 模块的收口），本方法负责资格校验、去重与定价快照。
     */
    @Transactional(readOnly = true)
    public List<OrderableLessonView> orderableLessonsForCaller(UUID studentAccountId,
                                                               List<BookingApplicationService.UpcomingBookingView> candidates) {
        var caller = CallerContext.require();
        if (caller.role() != CallerRole.PARENT && caller.role() != CallerRole.ADMIN) {
            throw new AccessDeniedException("仅家长或运营可查看孩子的可下单课程");
        }
        if (caller.role() == CallerRole.PARENT
                && !parentService.isActiveGuardian(caller.accountId(), studentAccountId)) {
            throw new AccessDeniedException("仅已确认监护关系的家长可查看孩子的可下单课程");
        }
        return candidates.stream()
                .filter(b -> "CONFIRMED".equals(b.status()))
                .filter(b -> orderRepository.findByBookingId(b.bookingId()).isEmpty())
                .map(b -> {
                    long priceCents = resolvePriceCents(b.teacherId(), b.subject());
                    return new OrderableLessonView(b.bookingId(), b.subject(),
                            b.startAt() == null ? null : b.startAt().toString(), priceCents);
                }).toList();
    }

    public Page<Order> ordersOfPayer(UUID payerParentId,
                                                                     Pageable pageable) {
        return orderRepository.findByPayerParentIdOrderByCreatedAtDesc(payerParentId, pageable);
    }

    /** 订单详情（对象级授权）：仅付款方、课程参与双方或运营可查看。 */
    @Transactional(readOnly = true)
    public OrderView detailsForCaller(UUID orderId) {
        var caller = CallerContext.require();
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("订单不存在"));
        boolean allowed = caller.role() == CallerRole.ADMIN
                || order.getPayerParentId().equals(caller.accountId())
                || order.getStudentId().equals(caller.accountId())
                || order.getTeacherId().equals(caller.accountId());
        if (!allowed) {
            throw new AccessDeniedException("仅订单相关方可查看订单详情");
        }
        return OrderView.from(order);
    }

    @Transactional(readOnly = true)
    public Order get(UUID orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("订单不存在"));
    }

    @Transactional(readOnly = true)
    public long countOrders() {
        return orderRepository.count();
    }

    /**
     * 定价解析（三分支，质量加固 2026-10-10）：
     * ① 在售条目 → 当前有效价格；
     * ② 明确存在但已下架（active=false）→ 拒绝新订单（「停止销售」不得回落默认价绕过）；
     * ③ 从未设置过该科目定价 → 保留既有 ¥300 兜底（合法兜底场景，勿随下架修复一并破坏）。
     */
    private long resolvePriceCents(UUID teacherId, String subject) {
        var matched = teacherService.offeringsPriceOf(teacherId).stream()
                .filter(o -> o.subject().equals(subject))
                .findFirst();
        if (matched.isPresent()) {
            return matched.get().pricePerHourCents();
        }
        if (teacherService.hasOfferingIncludingInactive(teacherId, subject)) {
            throw new IllegalStateException("该课程已停止销售，不能创建新订单");
        }
        return DEFAULT_LESSON_PRICE_CENTS;
    }

    private Order create(UUID bookingId, UUID payerParentId, UUID studentId, UUID teacherId,
                         Order.OrderType type, long amountCents, String subject,
                         OffsetDateTime lessonStartAt) {
        orderRepository.findByBookingId(bookingId).ifPresent(o -> {
            throw new IllegalStateException("该预约已存在订单，严禁重复下单");
        });
        String snapshot = reconciliationSnapshot(subject, amountCents, lessonStartAt, type);
        Order order = new Order(nextOrderNo(), bookingId, payerParentId, studentId, teacherId,
                type, amountCents, snapshot);
        Order saved = orderRepository.save(order);
        outboxWriter.write("order.created", "Order", saved.getId(),
                Map.of("orderNo", saved.getOrderNo(), "amountCents", amountCents));
        return saved;
    }

    private String nextOrderNo() {
        return "NUR" + OffsetDateTime.now().toLocalDate().toString().replace("-", "")
                + String.format("%08d", RANDOM.nextInt(100_000_000));
    }

    private String reconciliationSnapshot(String subject, long amountCents, OffsetDateTime startAt,
                                          Order.OrderType type) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "subject", subject,
                    "amountCents", amountCents,
                    "lessonStartAt", startAt == null ? "" : startAt.toString(),
                    "orderType", type.name(),
                    "snapshottedAt", OffsetDateTime.now().toString()));
        } catch (Exception e) {
            throw new IllegalStateException("对账快照生成失败", e);
        }
    }
}
