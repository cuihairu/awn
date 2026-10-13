package site.awn.promotion.api;

import java.time.Instant;

/** 优惠券模板视图 */
public record CouponTemplateView(
        String templateNo,
        String title,
        String discountKind,
        long discountValue,
        long minSpend,
        Instant validFrom,
        Instant validUntil,
        int totalQuota,
        int issuedCount) {
}
