package site.awn.promotion.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import site.awn.promotion.domain.Coupon;
import site.awn.promotion.domain.CouponStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface CouponRepository extends JpaRepository<Coupon, Long> {

    Optional<Coupon> findByCouponNo(String couponNo);

    List<Coupon> findTop100ByUserIdOrderByIdDesc(Long userId);

    boolean existsByTemplateIdAndUserId(Long templateId, Long userId);

    /** 守卫核销：仅 UNUSED 生效；返回 0 表示已被核销或不存在 */
    @Modifying
    @Query("UPDATE Coupon c SET c.status = site.awn.promotion.domain.CouponStatus.USED, c.usedAt = :usedAt "
            + "WHERE c.couponNo = :couponNo AND c.status = site.awn.promotion.domain.CouponStatus.UNUSED")
    int tryMarkUsed(@Param("couponNo") String couponNo, @Param("usedAt") Instant usedAt);

    Optional<Coupon> findByCouponNoAndStatus(String couponNo, CouponStatus status);
}
