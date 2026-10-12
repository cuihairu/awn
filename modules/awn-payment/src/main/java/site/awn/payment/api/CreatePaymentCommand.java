package site.awn.payment.api;

/** 创建支付单命令：金额为最小货币单位整数。 */
public record CreatePaymentCommand(
        String orderNo,
        String channel,
        String currency,
        long amount
) {
}
