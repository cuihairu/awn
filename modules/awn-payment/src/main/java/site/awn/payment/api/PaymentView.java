package site.awn.payment.api;

import java.time.Instant;

/**
 * 支付单视图。
 *
 * @param retryable 本单可重试：处于 FAILED、所属订单仍是待支付且未过支付截止期、
 *                  且该订单没有其他在途或已成功的支付单时为 true。
 *                  客户端据此显示「可重新支付 / 可换渠道」，不据此推断支付结果。
 */
public record PaymentView(
        String paymentNo,
        String orderNo,
        String channel,
        String channelTxnNo,
        String status,
        String currency,
        long amount,
        Instant paidAt,
        boolean retryable) {
}
