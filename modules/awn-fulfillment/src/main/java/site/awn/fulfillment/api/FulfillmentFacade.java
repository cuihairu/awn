package site.awn.fulfillment.api;

/**
 * 履约对外接口：交付流程与异常处理。
 * 支付成功不自动等于交付成功：履约单由 PAYMENT_SUCCEEDED 事件创建为
 * PENDING_SELLER，完成需卖家交付 + 平台核验。
 */
public interface FulfillmentFacade {

    FulfillmentView fulfillmentOf(String orderNo);

    /** 卖家交付（evidenceUrl 仅允许白名单存储域）：PENDING_SELLER → PENDING_VERIFICATION */
    FulfillmentView deliverSeller(DeliverCommand command);

    /** 平台核验：通过 → COMPLETED；拒绝 → FAILED（进入异常流程） */
    FulfillmentView verifyDelivery(VerifyDeliveryCommand command);

    /** 履约失败上报（卖家取消 / 超时 / 异常）：进入 FAILED 并发 FULFILLMENT_FAILED 事件 */
    FulfillmentView reportFailure(String orderNo, String reason);

    /** 订单侧取消联动：履约单 CANCELLED */
    FulfillmentView cancel(String orderNo, String reason);
}
