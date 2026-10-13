package site.awn.api;

/**
 * 创建支付单请求：客户端只选择渠道；
 * 金额与币种由服务端按订单应付金额填写，不接受前端传值。
 */
public record CreatePaymentRequest(String channel) {
}
