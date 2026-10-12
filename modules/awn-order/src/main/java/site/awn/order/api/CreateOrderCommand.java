package site.awn.order.api;

import java.util.List;

/**
 * 创建订单命令。幂等键必填：同键同请求重放返回原订单，同键不同请求拒绝。
 */
public record CreateOrderCommand(
        String idempotencyKey,
        Long userId,
        String currency,
        List<OrderLineCommand> lines) {

    public CreateOrderCommand {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey is required");
        }
        if (idempotencyKey.length() > 128) {
            throw new IllegalArgumentException("idempotencyKey too long");
        }
        if (userId == null) {
            throw new IllegalArgumentException("userId is required");
        }
        if (currency == null || currency.length() != 3) {
            throw new IllegalArgumentException("currency must be ISO-4217 alpha-3");
        }
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("at least one order line is required");
        }
        lines = List.copyOf(lines);
    }
}
