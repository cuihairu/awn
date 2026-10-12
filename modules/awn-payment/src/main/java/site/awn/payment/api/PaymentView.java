package site.awn.payment.api;

import java.time.Instant;

/** 支付单视图。 */
public record PaymentView(
        String paymentNo,
        String orderNo,
        String channel,
        String channelTxnNo,
        String status,
        String currency,
        long amount,
        Instant paidAt
) {
}
