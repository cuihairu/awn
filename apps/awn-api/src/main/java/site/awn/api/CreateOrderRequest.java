package site.awn.api;

import java.util.List;

/**
 * 创建订单请求（POST /api/v1/orders）。快照字段（商品名/票档）由调用方传入，
 * 与模块层 {@code CreateOrderCommand} 同语义：Phase 02 商品 API 落地前防止客户端伪造快照。
 * 金额由服务端在模块层计算，客户端不得传总价。
 */
public record CreateOrderRequest(
        Long userId,
        String currency,
        List<Line> lines) {

    public record Line(
            String poolCode,
            String listingNo,
            String productId,
            String productNameSnapshot,
            String sessionId,
            String ticketCategorySnapshot,
            int quantity,
            long unitPrice,
            long discountAmount,
            long feeAmount) {
    }
}
