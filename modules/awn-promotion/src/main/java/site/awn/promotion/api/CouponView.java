package site.awn.promotion.api;

import java.time.Instant;

/** 券实例视图（含模板摘要，避免视图侧二次查询） */
public record CouponView(
        String couponNo,
        String templateNo,
        String title,
        long discountValue,
        long minSpend,
        long userId,
        String status,
        Instant issuedAt,
        Instant usedAt) {
}
