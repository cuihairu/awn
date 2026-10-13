package site.awn.promotion;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import site.awn.promotion.api.CouponFacade;
import site.awn.promotion.api.CouponTemplateView;
import site.awn.promotion.api.CouponView;
import site.awn.promotion.api.CreateCouponTemplateCommand;
import site.awn.promotion.domain.CouponConflictException;
import site.awn.promotion.domain.CouponNotFoundException;
import site.awn.support.AbstractPostgresIntegrationTest;

import org.springframework.jdbc.core.JdbcTemplate;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * promotion 模块：优惠券创建、发放、核销与限额。
 * 验收口径（module-boundaries.md）：券创建 / 使用 / 限额 + 集成测试。
 * 边界：本模块不直接修改订单最终金额——核销只改自身状态，订单侧联动属后续裁决。
 */
@SpringBootTest
class PromotionModuleTests extends AbstractPostgresIntegrationTest {

    @Autowired CouponFacade promotions;
    @Autowired JdbcTemplate jdbc;

    @Test
    void templateCreationValidatesBusinessRules() {
        // 面额、配额必须为正；有效期窗口必须自洽
        assertThatThrownBy(() -> promotions.createTemplate(template(0, 100, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("discount");
        assertThatThrownBy(() -> promotions.createTemplate(template(500, 0, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("quota");
        assertThatThrownBy(() -> promotions.createTemplate(
                template(500, 100, Instant.now(), Instant.now().minusSeconds(3600))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("valid");

        CouponTemplateView view = promotions.createTemplate(template(1500, 100, null, null));
        assertThat(view.templateNo()).isNotBlank();
        assertThat(view.issuedCount()).isZero();
        assertThat(view.totalQuota()).isEqualTo(100);
        assertThat(view.discountValue()).isEqualTo(1500);
    }

    @Test
    void issueEnforcesTotalQuota() {
        CouponTemplateView template = promotions.createTemplate(template(800, 2, null, null));

        CouponView first = promotions.issue(template.templateNo(), userId());
        CouponView second = promotions.issue(template.templateNo(), userId());
        assertThat(first.status()).isEqualTo("UNUSED");
        assertThat(second.status()).isEqualTo("UNUSED");

        // 配额耗尽：第三张必须被拒（守卫递增裁决，不超发）
        assertThatThrownBy(() -> promotions.issue(template.templateNo(), userId()))
                .isInstanceOf(CouponConflictException.class)
                .hasMessageContaining("quota");
    }

    @Test
    void issueEnforcesPerUserLimitAndValidityWindow() {
        CouponTemplateView template = promotions.createTemplate(template(600, 100, null, null));
        long user = userId();
        promotions.issue(template.templateNo(), user);

        // 同模板每人限领一张
        assertThatThrownBy(() -> promotions.issue(template.templateNo(), user))
                .isInstanceOf(CouponConflictException.class)
                .hasMessageContaining("per user");

        // 窗口外（已过期 / 未开始）拒绝发放
        CouponTemplateView expired = promotions.createTemplate(
                template(600, 100, Instant.now().minusSeconds(7200), Instant.now().minusSeconds(3600)));
        assertThatThrownBy(() -> promotions.issue(expired.templateNo(), userId()))
                .isInstanceOf(CouponConflictException.class)
                .hasMessageContaining("window");

        CouponTemplateView future = promotions.createTemplate(
                template(600, 100, Instant.now().plusSeconds(3600), Instant.now().plusSeconds(7200)));
        assertThatThrownBy(() -> promotions.issue(future.templateNo(), userId()))
                .isInstanceOf(CouponConflictException.class)
                .hasMessageContaining("window");
    }

    @Test
    void redeemMarksUsedExactlyOnce() {
        CouponTemplateView template = promotions.createTemplate(template(900, 10, null, null));
        CouponView coupon = promotions.issue(template.templateNo(), userId());

        CouponView used = promotions.redeem(coupon.couponNo());
        assertThat(used.status()).isEqualTo("USED");
        assertThat(used.usedAt()).isNotNull();

        // 重复核销必须被拒（守卫迁移裁决）
        assertThatThrownBy(() -> promotions.redeem(coupon.couponNo()))
                .isInstanceOf(CouponConflictException.class)
                .hasMessageContaining("already used");

        // 未知券号不可核销
        assertThatThrownBy(() -> promotions.redeem("CPN-no-such-" + UUID.randomUUID()))
                .isInstanceOf(CouponNotFoundException.class);
    }

    @Test
    void redeemRejectsCouponWhoseWindowClosedAfterIssue() {
        CouponTemplateView template = promotions.createTemplate(
                template(900, 10, Instant.now().minusSeconds(60), Instant.now().plusSeconds(3600)));
        CouponView coupon = promotions.issue(template.templateNo(), userId());

        // 发放后窗口关闭（测试库直接收缩有效期）：核销必须被拒
        jdbc.update("UPDATE coupon_templates SET valid_until = now() - interval '1 minute' WHERE template_no = ?",
                template.templateNo());
        assertThatThrownBy(() -> promotions.redeem(coupon.couponNo()))
                .isInstanceOf(CouponConflictException.class)
                .hasMessageContaining("window");
    }

    @Test
    void couponsOfListsUserCoupons() {
        long user = userId();
        CouponTemplateView template = promotions.createTemplate(template(300, 10, null, null));
        CouponView coupon = promotions.issue(template.templateNo(), user);

        List<CouponView> mine = promotions.couponsOf(user);
        assertThat(mine).anyMatch(c -> c.couponNo().equals(coupon.couponNo()));
        assertThat(mine).allMatch(c -> c.userId() == user);
    }

    // ------------------------------------------------------------------

    /** 每个用例独立的用户号：共享测试库从不截断，断言按用户隔离 */
    private long userId() {
        return 5000L + Math.abs(UUID.randomUUID().getLeastSignificantBits() % 1_000_000L);
    }

    private CreateCouponTemplateCommand template(long discountValue, int totalQuota,
                                                 Instant validFrom, Instant validUntil) {
        return new CreateCouponTemplateCommand("满减券-" + UUID.randomUUID().toString().substring(0, 6),
                discountValue, 0, validFrom, validUntil, totalQuota);
    }
}
