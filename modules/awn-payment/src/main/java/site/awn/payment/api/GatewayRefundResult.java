package site.awn.payment.api;

/** 渠道退款受理结果。 */
public record GatewayRefundResult(
        String channelRefundNo,
        GatewayChannelStatus status
) {
}
