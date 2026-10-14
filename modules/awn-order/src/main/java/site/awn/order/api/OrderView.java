package site.awn.order.api;

import site.awn.order.domain.OrderStatus;

import java.time.Instant;
import java.util.List;

/**
 * 订单视图。金额为最小货币单位整数。
 *
 * <p>倒计时口径（契约「订单支付倒计时」）：{@code serverTime} 是响应生成时的服务端时间，
 * 客户端以它为锚点推算 {@code payExpiresInSeconds} 的流逝，不得用本机时钟判定超时；
 * 非 {@code PENDING_PAYMENT} 状态的 {@code payExpiresInSeconds} 恒为 0。
 */
public record OrderView(
        String orderNo,
        OrderStatus status,
        String currency,
        long totalAmount,
        long discountAmount,
        long feeAmount,
        long payAmount,
        Instant expiresAt,
        List<OrderItemView> items,
        Instant serverTime,
        long payExpiresInSeconds) {

    public OrderView {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
