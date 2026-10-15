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
            String seatNo,
            String productId,
            String productNameSnapshot,
            String sessionId,
            String ticketCategorySnapshot,
            int quantity,
            long unitPrice,
            long discountAmount,
            long feeAmount,
            List<Attendee> attendees) {
    }

    /** 观演人实名（可选，一票一证：数量必须与行 quantity 一致，违反 400 / 同场次同证件 409） */
    public record Attendee(String name, String idNumber) {
    }
}
