package com.nurture.order.api;

import com.nurture.booking.app.BookingApplicationService;
import com.nurture.order.app.OrderApplicationService;
import com.nurture.parent.app.ParentApplicationService;
import com.nurture.shared.security.CallerContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 订单中心 API — 家庭统一支付视角的订单与对账快照。
 * 薄控制器：预约视图经 booking.app 获取后传入 order.app，业务规则全部在应用服务。
 */
@RestController
@RequestMapping("/api/v1/order")
@Tag(name = "order / 订单中心", description = "课时交易订单、订单状态机、对账快照")
@SecurityRequirement(name = "bearerJWT")
public class OrderController {

    private final OrderApplicationService orderService;
    private final BookingApplicationService bookingService;
    private final ParentApplicationService parentService;

    public OrderController(OrderApplicationService orderService, BookingApplicationService bookingService,
                           ParentApplicationService parentService) {
        this.orderService = orderService;
        this.bookingService = bookingService;
        this.parentService = parentService;
    }

    public record CreateOrderRequest(@NotNull UUID bookingId) {
    }

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('STUDENT') or hasRole('PARENT') or hasRole('ADMIN')")
    @Operation(summary = "为已确认预约创建订单", description = "一课一单，重复下单被状态机拒绝；"
            + "学生仅本人预约可下单，家长须与学生存在已确认监护绑定")
    public OrderApplicationService.OrderView create(@RequestBody CreateOrderRequest request) {
        var booking = bookingService.orderViewOf(request.bookingId());
        return orderService.createForCaller(booking);
    }

    @GetMapping("/me/orders")
    @Operation(summary = "我的订单列表（家长付款视角）")
    public Page<OrderApplicationService.OrderView> myOrders(@RequestParam(defaultValue = "0") int page,
                                                            @RequestParam(defaultValue = "10") int size) {
        return orderService.ordersOfPayerView(CallerContext.require().accountId(),
                org.springframework.data.domain.PageRequest.of(page, size));
    }

    @GetMapping("/orders/{orderId}")
    @Operation(summary = "订单详情（含对账快照）", description = "仅订单付款方、课程参与双方或运营可查看")
    public OrderApplicationService.OrderView get(@PathVariable UUID orderId) {
        return orderService.detailsForCaller(orderId);
    }

    @GetMapping("/me/refundable-orders")
    @PreAuthorize("hasRole('STUDENT') or hasRole('PARENT')")
    public Page<OrderApplicationService.OrderView> refundableOrders(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1 || size > 50) throw new IllegalArgumentException("页码或每页数量无效");
        return orderService.refundEligibleOrdersForCaller(PageRequest.of(page, size));
    }

    @GetMapping("/bookings:orderable")
    @PreAuthorize("hasRole('PARENT') or hasRole('ADMIN')")
    @Operation(summary = "孩子的可下单课程（ACTIVE 监护家长视角）",
            description = "studentId 为学生档案 ID；仅返回导师已确认且尚未生成订单的课程，金额为服务端定价快照")
    public java.util.List<OrderApplicationService.OrderableLessonView> orderableForChild(
            @RequestParam UUID studentId) {
        var studentAccountId = parentService.accountIdOfStudentProfile(studentId);
        if (studentAccountId == null) {
            throw new IllegalArgumentException("学生档案不存在");
        }
        var candidates = bookingService.confirmedForStudent(studentAccountId);
        return orderService.orderableLessonsForCaller(studentAccountId, candidates);
    }
}
