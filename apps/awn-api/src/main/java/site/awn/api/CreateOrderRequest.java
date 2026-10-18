package site.awn.api;

import java.util.List;

/**
 * 创建订单请求（POST /api/v1/orders）。调用方提供商品标识、数量和展示快照；金额字段会与目录 / 挂牌权威价格核对。
 * {@code userId} 为兼容旧客户端保留，服务端忽略该值并从已验证的 bearer token 取用户身份。
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
