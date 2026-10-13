package site.awn.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import site.awn.order.api.CreateOrderCommand;
import site.awn.order.api.OrderFacade;
import site.awn.order.api.OrderLineCommand;
import site.awn.order.api.OrderView;

import java.net.URI;
import java.util.List;

/**
 * 交易接口（组合根）：订单创建/查询/取消。
 * 金额由服务端在模块层计算（OrderWriteService），客户端只传单价与优惠；
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
            @RequestBody CreateOrderRequest request) {
        OrderView order = orders.createOrder(toCommand(idempotencyKey, request));
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(URI.create("/api/v1/orders/" + order.orderNo()))
                .body(order);
    }

    @GetMapping("/api/v1/orders/{orderId}")
    public OrderView order(@PathVariable String orderId) {
        return orders.orderOf(orderId);
    }

    @PostMapping("/api/v1/orders/{orderId}/cancel")
    public OrderView cancel(@PathVariable String orderId) {
        orders.cancel(orderId);
        return orders.orderOf(orderId);
    }

    private CreateOrderCommand toCommand(String idempotencyKey, CreateOrderRequest request) {
        List<OrderLineCommand> lines = request.lines().stream()
                .map(l -> new OrderLineCommand(l.poolCode(), l.listingNo(), l.productId(),
                        l.productNameSnapshot(), l.sessionId(), l.ticketCategorySnapshot(),
                        l.quantity(), l.unitPrice(), l.discountAmount(), l.feeAmount()))
                .toList();
        return new CreateOrderCommand(idempotencyKey, request.userId(), request.currency(), lines);
    }
}
