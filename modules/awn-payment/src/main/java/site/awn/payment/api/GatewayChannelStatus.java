package site.awn.payment.api;

/** 渠道侧受理状态。 */
public enum GatewayChannelStatus {
    PENDING,
    SUCCEEDED,
    FAILED,
    UNKNOWN
}
