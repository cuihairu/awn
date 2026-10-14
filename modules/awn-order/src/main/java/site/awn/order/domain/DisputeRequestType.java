package site.awn.order.domain;

/** 买家开争议时请求的处理方式：REFUND 必填金额，REDELIVER 不得带金额 */
public enum DisputeRequestType {
    REFUND,
    REDELIVER
}
