package site.awn.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletRequest;
import site.awn.api.auth.AuthenticatedUser;
import site.awn.order.api.AttendeeCommand;
import site.awn.order.api.CreateOrderCommand;
import site.awn.order.api.OrderFacade;
import site.awn.order.api.OrderLineCommand;
import site.awn.order.api.OrderView;

import java.net.URI;
import java.util.List;

/**
 * 交易接口（组合根）：订单创建/查询/取消。
 * 金额由模块层向目录 / 挂牌权威价格核对，优惠与费用在服务端规则落地前必须为 0；
 * 幂等键以 {@code Idempotency-Key} 请求头传入，与模块层 CreateOrderCommand 同一语义。
 */
@RestController
public class OrderController {

    private final OrderFacade orders;

    public OrderController(OrderFacade orders) {
        this.orders = orders;
    }

    @PostMapping("/api/v1/orders")
    public ResponseEntity<OrderView> createOrder(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody CreateOrderRequest request, HttpServletRequest httpRequest) {
        long userId = AuthenticatedUser.userId(httpRequest);
        OrderView order = orders.createOrder(toCommand(idempotencyKey, request, userId));
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(URI.create("/api/v1/orders/" + order.orderNo()))
                .body(order);
    }

    @GetMapping("/api/v1/orders/{orderId}")
    public OrderView order(@PathVariable String orderId, HttpServletRequest request) {
        requireBuyerOwner(orderId, request);
        return orders.orderOf(orderId);
    }

    @PostMapping("/api/v1/orders/{orderId}/cancel")
    public OrderView cancel(@PathVariable String orderId, HttpServletRequest request) {
        requireBuyerOwner(orderId, request);
        orders.cancel(orderId);
        return orders.orderOf(orderId);
    }

    private void requireBuyerOwner(String orderId, HttpServletRequest request) {
        long userId = AuthenticatedUser.userId(request);
        AuthenticatedUser.requireOwner(orders.ownerOf(orderId), userId);
    }

    private CreateOrderCommand toCommand(String idempotencyKey, CreateOrderRequest request, long userId) {
        List<OrderLineCommand> lines = request.lines().stream()
                .map(l -> new OrderLineCommand(l.poolCode(), l.listingNo(), l.seatNo(),
                        l.productId(), l.productNameSnapshot(), l.sessionId(),
                        l.ticketCategorySnapshot(), l.quantity(), l.unitPrice(),
                        l.discountAmount(), l.feeAmount(), attendees(l.attendees())))
                .toList();
        return new CreateOrderCommand(idempotencyKey, userId, request.currency(), lines);
    }

    private static List<AttendeeCommand> attendees(List<CreateOrderRequest.Attendee> attendees) {
        if (attendees == null) {
            return null;
        }
        return attendees.stream()
                .map(a -> new AttendeeCommand(a.name(), a.idNumber()))
                .toList();
    }
}
