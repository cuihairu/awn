package site.awn.payment.api;

/** 渠道下单命令：金额为最小货币单位整数。 */
public record GatewayChargeCommand(
        String paymentNo,
        String orderNo,
        String currency,
        long amount
) {
}
