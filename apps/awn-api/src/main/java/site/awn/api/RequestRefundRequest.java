package site.awn.api;

/** 发起退款请求：金额为最小货币单位整数；幂等键走 Idempotency-Key 头。 */
public record RequestRefundRequest(String paymentNo, long amount, String reason) {
}
