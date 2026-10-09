package com.nurture.order.api;

import com.nurture.booking.app.BookingApplicationService;
import com.nurture.order.app.OrderApplicationService;
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

    public OrderController(OrderApplicationService orderService, BookingApplicationService bookingService) {
        this.orderService = orderService;
        this.bookingService = bookingService;
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
}
