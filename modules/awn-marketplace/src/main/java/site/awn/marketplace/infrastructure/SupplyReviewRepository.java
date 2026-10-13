package site.awn.marketplace.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import site.awn.marketplace.domain.SupplyReview;
import site.awn.marketplace.domain.SupplyReviewStatus;

import java.util.Optional;

public interface SupplyReviewRepository extends JpaRepository<SupplyReview, Long> {

    Optional<SupplyReview> findByReviewNo(String reviewNo);

    /**
     * 额度守卫迁移：仅当审核已通过且额度未耗尽时消耗 1。
     * 并发挂牌由这一条 UPDATE 的行锁 + 谓词裁决，赢者才能创建 platform_guaranteed 挂牌。
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE SupplyReview r
               SET r.consumedQuantity = r.consumedQuantity + 1
             WHERE r.id = :id
               AND r.status = :status
               AND r.consumedQuantity < r.quantity
            """)
    int consumeQuota(@Param("id") Long id, @Param("status") SupplyReviewStatus status);
}
