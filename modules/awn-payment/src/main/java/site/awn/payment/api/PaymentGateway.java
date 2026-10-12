package site.awn.payment.api;

import java.util.Map;

/**
 * 支付渠道抽象：与具体渠道解耦（docs/adr、docs/architecture/payment-state-machine.md）。
 * 每个渠道一个实现；测试适配器（沙箱渠道）可模拟成功/超时/失败。
 */
public interface PaymentGateway {

    /** 渠道标识，如 sandbox / alipay / wechat */
    String channelCode();

    /** 渠道下单（受理），返回渠道交易号与受理状态 */
    GatewayChargeResult createCharge(GatewayChargeCommand command);

    /** 渠道退款（受理），返回渠道退款编号与受理状态 */
    GatewayRefundResult createRefund(GatewayRefundCommand command);

    /** 渠道查询：对账与结果未知时先查询，不盲目再次发起 */
    GatewayQueryResult queryCharge(String channelTxnNo);

    /** 渠道退款查询：退款对账使用 */
    GatewayQueryResult queryRefund(String channelRefundNo);

    /** 回调验签：失败即拒绝处理 */
    boolean verifyCallback(String rawBody, Map<String, String> headers);
}
