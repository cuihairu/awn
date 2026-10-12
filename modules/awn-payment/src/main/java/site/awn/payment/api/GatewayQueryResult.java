package site.awn.payment.api;

/** 渠道查询结果。 */
public record GatewayQueryResult(
        String channelTxnNo,
        GatewayChannelStatus status
) {
}
