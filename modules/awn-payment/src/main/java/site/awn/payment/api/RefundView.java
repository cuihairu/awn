package site.awn.payment.api;

/** 退款单视图。 */
public record RefundView(
        String refundNo,
        String paymentNo,
        String orderNo,
        String channelRefundNo,
        String status,
        String currency,
        long amount,
        String reason
) {
}
