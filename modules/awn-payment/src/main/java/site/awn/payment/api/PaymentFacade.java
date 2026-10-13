package site.awn.payment.api;

import java.util.List;
import java.util.Map;

/**
 * 支付模块对外接口（跨模块只允许调用本接口）。
 * 回调处理保证：验签失败拒绝；重复回调只生效一次；
 * 回调与订单超时释放竞态时进入「待核实」而非盲目确认。
 */
public interface PaymentFacade {

    /** 创建支付单并向渠道发起受理 */
    PaymentView createPayment(CreatePaymentCommand command);

    PaymentView payment(String paymentNo);

    /**
     * 处理支付回调：验签 → 渠道交易号定位支付单 → 金额/币种/渠道校验 →
     * 条件更新状态（幂等）→ 同事务写 Outbox 支付事件。
     */
    PaymentView handleCallback(PaymentCallbackCommand command);

    /** 发起退款（幂等键判重；累计退款金额不得超过支付金额） */
    RefundView requestRefund(RequestRefundCommand command);

    RefundView refund(String refundNo);

    /** 处理退款回调（幂等）：成功后联动支付单退款状态并写 Outbox 事件 */
    RefundView handleRefundCallback(RefundCallbackCommand command);

    /**
     * 支付/退款对账：对未终态支付单与退款单向渠道查询，
     * 发现本地与渠道状态差异即报告，不自动修复。
     */
    PaymentReconciliationReport reconcile();

    /**
     * UNKNOWN 支付单核实（Worker 调用）：逐单向渠道查询并迁移到查实后的状态。
     * 查实成功但订单已终态（库存已释放）→ 异常单处理：告警 + 原路退款，不假装履约。
     */
    PaymentUnknownResolutionResult resolveUnknownPayments();
}
