package site.awn.promotion.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import site.awn.promotion.domain.CouponTemplate;

import java.util.Optional;

public interface CouponTemplateRepository extends JpaRepository<CouponTemplate, Long> {

    Optional<CouponTemplate> findByTemplateNo(String templateNo);

    /**
     * 守卫递增发放数：issued_count < total_quota 才生效。
     * 返回 0 表示配额已耗尽（并发下由本条件 UPDATE 裁决，配合 CHECK 约束兜底）。
     */
    @Modifying
    @Query("UPDATE CouponTemplate t SET t.issuedCount = t.issuedCount + 1 WHERE t.id = :id AND t.issuedCount < t.totalQuota")
    int tryConsumeQuota(@Param("id") Long id);
}
