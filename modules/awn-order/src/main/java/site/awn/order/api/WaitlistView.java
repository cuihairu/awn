package site.awn.order.api;

import java.time.Instant;

/**
 * 候补条目视图。
 *
 * @param position 同池排队位次（从 1 起）；非排队状态为 null
 * @param orderNo 递补生成的订单号；未递补为 null
 * @param payExpiresInSeconds 递补订单的剩余支付秒数（服务端时间口径）；未递补为 null
 */
public record WaitlistView(
        String waitlistNo,
        String status,
        Long userId,
        String sessionId,
        String poolCode,
        String productId,
        String productName,
        int quantity,
        long unitPrice,
        String currency,
        Integer position,
        String orderNo,
        Long payExpiresInSeconds,
        Instant createdAt) {
}
