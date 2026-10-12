package site.awn.payment.api;

/** 渠道退款命令。 */
public record GatewayRefundCommand(
        String refundNo,
        String channelTxnNo,
        String currency,
        long amount
) {
}
