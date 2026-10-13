package site.awn.promotion.api;

import java.time.Instant;

/** 创建优惠券模板：面额（最小货币单位整数）、门槛、有效期窗口、发放配额 */
public record CreateCouponTemplateCommand(
        String title,
        long discountValue,
        long minSpend,
        Instant validFrom,
        Instant validUntil,
        int totalQuota) {
}
