package site.awn.order.api;

/**
 * 订单行视图。
 * 凭证型行（挂牌票品）的 {@code poolCode} / {@code reservationNo} 为 null，
 * {@code listingNo} / {@code unitNo} 有值；数量型行反之。
 */
public record OrderItemView(
        String poolCode,
        String productId,
        String productNameSnapshot,
        String sessionId,
        String ticketCategorySnapshot,
        int quantity,
        long unitPrice,
        long discountAmount,
        long feeAmount,
        long lineTotal,
        String reservationNo,
        String listingNo,
        String unitNo) {
}
