package site.awn.order.domain;

/** 争议裁决结论：裁决只记录结论，退款执行走支付模块既有退款流（docs/architecture/dispute-handling.md） */
public enum DisputeResolution {
    REFUND_APPROVED,
    REDELIVER_APPROVED,
    REJECTED
}
