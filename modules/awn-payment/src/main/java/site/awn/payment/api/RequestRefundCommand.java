package site.awn.payment.api;

/** 发起退款命令：幂等键由调用方生成，同键同请求返回原退款单。 */
public record RequestRefundCommand(
        String paymentNo,
        String idempotencyKey,
        long amount,
        String reason
) {
}
