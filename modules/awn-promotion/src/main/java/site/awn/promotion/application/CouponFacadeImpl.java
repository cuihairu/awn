package site.awn.promotion.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.awn.promotion.api.CouponFacade;
import site.awn.promotion.api.CouponTemplateView;
import site.awn.promotion.api.CouponView;
import site.awn.promotion.api.CreateCouponTemplateCommand;
import site.awn.promotion.domain.Coupon;
import site.awn.promotion.domain.CouponConflictException;
import site.awn.promotion.domain.CouponNotFoundException;
import site.awn.promotion.domain.CouponTemplate;
import site.awn.promotion.infrastructure.CouponRepository;
import site.awn.promotion.infrastructure.CouponTemplateRepository;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 优惠券用例实现。
 * 限额语义：
 * - 配额：发放走守卫递增（issued_count < total_quota），并发不超发，CHECK 约束兜底；
 * - 每人限领一张：先查重再插入，(template_id, user_id) 唯一约束兜底竞态；
 * - 窗口：发放与核销都要求 now ∈ [validFrom, validUntil]（单边 null 即无界）。
 */
@Service
public class CouponFacadeImpl implements CouponFacade {

    private static final String DISCOUNT_KIND_FIXED = "FIXED_AMOUNT";

    private final CouponTemplateRepository templates;
    private final CouponRepository coupons;

    public CouponFacadeImpl(CouponTemplateRepository templates, CouponRepository coupons) {
        this.templates = templates;
        this.coupons = coupons;
    }

    @Override
    @Transactional
    public CouponTemplateView createTemplate(CreateCouponTemplateCommand command) {
        if (command.discountValue() <= 0) {
            throw new IllegalArgumentException("discount value must be positive: " + command.discountValue());
        }
        if (command.totalQuota() <= 0) {
            throw new IllegalArgumentException("total quota must be positive: " + command.totalQuota());
        }
        if (command.validFrom() != null && command.validUntil() != null
                && command.validUntil().isBefore(command.validFrom())) {
            throw new IllegalArgumentException("valid until must not be before valid from");
        }
        CouponTemplate template = new CouponTemplate(
                "CT-" + UUID.randomUUID().toString().substring(0, 12),
                command.title() == null || command.title().isBlank() ? "优惠券" : command.title(),
                DISCOUNT_KIND_FIXED, command.discountValue(), Math.max(command.minSpend(), 0),
                command.validFrom(), command.validUntil(), command.totalQuota());
        return toView(templates.saveAndFlush(template));
    }

    @Override
    @Transactional
    public CouponView issue(String templateNo, long userId) {
        CouponTemplate template = templateOf(templateNo);
        Instant now = Instant.now();
        if (!template.acceptsAt(now)) {
            throw new CouponConflictException("coupon template not in issue window: " + templateNo);
        }
        // 每人限领一张：先查重；竞态由 (template_id, user_id) 唯一约束兜底
        if (coupons.existsByTemplateIdAndUserId(template.getId(), userId)) {
            throw new CouponConflictException("one coupon per user per template: " + templateNo);
        }
        // 配额守卫递增：0 行即配额耗尽（并发裁决），同事务回滚不影响其他模板
        if (templates.tryConsumeQuota(template.getId()) == 0) {
            throw new CouponConflictException("coupon quota exhausted: " + templateNo);
        }
        template.markIssued();
        Coupon coupon = coupons.saveAndFlush(
                new Coupon("CPN-" + UUID.randomUUID().toString().substring(0, 12), template.getId(), userId));
        return toView(coupon, template);
    }

    @Override
    @Transactional
    public CouponView redeem(String couponNo) {
        Coupon coupon = coupons.findByCouponNo(couponNo)
                .orElseThrow(() -> new CouponNotFoundException("coupon not found: " + couponNo));
        CouponTemplate template = templates.findById(coupon.getTemplateId())
                .orElseThrow(() -> new CouponNotFoundException("coupon template not found: " + couponNo));
        if (!template.acceptsAt(Instant.now())) {
            throw new CouponConflictException("coupon not in redemption window: " + couponNo);
        }
        Instant usedAt = Instant.now();
        if (coupons.tryMarkUsed(couponNo, usedAt) == 0) {
            throw new CouponConflictException("coupon already used: " + couponNo);
        }
        coupon.markUsed(usedAt);
        return toView(coupon, template);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CouponView> couponsOf(long userId) {
        Map<Long, CouponTemplate> templateCache = new HashMap<>();
        return coupons.findTop100ByUserIdOrderByIdDesc(userId).stream()
                .map(coupon -> toView(coupon, templateCache.computeIfAbsent(
                        coupon.getTemplateId(), id -> templates.findById(id).orElseThrow())))
                .toList();
    }

    // ------------------------------------------------------------------

    private CouponTemplate templateOf(String templateNo) {
        return templates.findByTemplateNo(templateNo)
                .orElseThrow(() -> new CouponNotFoundException("coupon template not found: " + templateNo));
    }

    private static CouponTemplateView toView(CouponTemplate t) {
        return new CouponTemplateView(t.getTemplateNo(), t.getTitle(), t.getDiscountKind(),
                t.getDiscountValue(), t.getMinSpend(), t.getValidFrom(), t.getValidUntil(),
                t.getTotalQuota(), t.getIssuedCount());
    }

    private static CouponView toView(Coupon c, CouponTemplate t) {
        return new CouponView(c.getCouponNo(), t.getTemplateNo(), t.getTitle(),
                t.getDiscountValue(), t.getMinSpend(), c.getUserId(), c.getStatus().name(),
                c.getIssuedAt(), c.getUsedAt());
    }
}
