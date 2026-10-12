package site.awn.order.api;

/** 订单行视图。 */
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
        String reservationNo) {
}
