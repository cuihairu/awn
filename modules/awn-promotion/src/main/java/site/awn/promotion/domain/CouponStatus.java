package site.awn.promotion.domain;

/** 券实例状态：UNUSED → USED（USED 为终态，退款回收不在本模块范围） */
public enum CouponStatus {
    UNUSED,
    USED
}
