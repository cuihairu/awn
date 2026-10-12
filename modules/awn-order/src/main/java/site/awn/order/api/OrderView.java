package site.awn.order.api;

import site.awn.order.domain.OrderStatus;

import java.time.Instant;
import java.util.List;

/** 订单视图。金额为最小货币单位整数。 */
public record OrderView(
        String orderNo,
        OrderStatus status,
        String currency,
        long totalAmount,
        long discountAmount,
        long feeAmount,
        long payAmount,
        Instant expiresAt,
        List<OrderItemView> items) {

    public OrderView {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
