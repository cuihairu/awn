package site.awn.promotion.api;

import java.util.List;

/**
 * promotion 模块对外接口。其他模块只允许通过本接口使用优惠券能力。
 * 边界：本模块不直接修改订单最终金额——订单侧如何消费券属后续设计裁决。
 */
public interface CouponFacade {

    /** 创建优惠券模板（面额 / 配额为正、有效期窗口自洽） */
    CouponTemplateView createTemplate(CreateCouponTemplateCommand command);

    /**
     * 发放一张券给用户。限额三重守卫：有效期窗口内、模板配额未耗尽
     * （守卫递增裁决，不超发）、同模板每人限领一张（唯一约束兜底）。
     */
    CouponView issue(String templateNo, long userId);

    /** 核销：UNUSED → USED（窗口内）。重复核销抛冲突。 */
    CouponView redeem(String couponNo);

    /** 用户已领的券（新 → 旧，限 100） */
    List<CouponView> couponsOf(long userId);
}
