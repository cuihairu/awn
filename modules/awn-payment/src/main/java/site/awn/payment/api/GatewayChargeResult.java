package site.awn.payment.api;

/** 渠道受理结果。 */
public record GatewayChargeResult(
        String channelTxnNo,
        GatewayChannelStatus status
) {
}
