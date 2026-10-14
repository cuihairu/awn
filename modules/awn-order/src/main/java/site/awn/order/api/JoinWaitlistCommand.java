package site.awn.order.api;

/**
 * 加入候补命令（契约 POST /api/v1/waitlist）。
 *
 * <p>票档信息是加入候补时的快照：候补不查商品接口、不核价格，
 * 排队期间商品改价不回溯历史条目。
 */
public record JoinWaitlistCommand(
        String sessionId,
        String poolCode,
        String productId,
        String productName,
        int quantity,
        long unitPrice,
        String currency,
        Long userId) {
}