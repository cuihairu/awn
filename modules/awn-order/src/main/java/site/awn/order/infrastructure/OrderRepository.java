package site.awn.order.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import site.awn.order.domain.Order;
import site.awn.order.domain.OrderStatus;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByOrderNo(String orderNo);

    Optional<Order> findByIdempotencyKey(String idempotencyKey);

    /**
     * 幂等键事务锁：同一幂等键的创建事务在此串行化，
     * 先到的提交后，后到的在锁内看到已提交订单，走重放或冲突分支。
     * 数据库唯一约束是兜底防线。
     * （外层 SELECT 1：pg_advisory_xact_lock 返回 void，JPA 需要可映射的标量结果）
     */
    @Query(value = "SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtext(:key))) AS lock", nativeQuery = true)
    void lockIdempotencyKey(@Param("key") String key);

    /**
     * 状态机守卫迁移：仅当当前状态等于 from 时迁移到 to。
     * 返回 0 表示状态已变化（竞态或非法迁移），调用方必须重读后显式处理，禁止盲目写状态。
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Order o
               SET o.status = :to
             WHERE o.orderNo = :orderNo
               AND o.status = :from
            """)
    int transition(@Param("orderNo") String orderNo,
                   @Param("from") OrderStatus from,
                   @Param("to") OrderStatus to);

    /** Worker 超时任务扫描：支付截止期已过的待支付订单号（按截止期排序，配合分页限批） */
    @Query("""
            SELECT o.orderNo
              FROM Order o
             WHERE o.status = :status
               AND o.expiresAt IS NOT NULL
               AND o.expiresAt <= :now
             ORDER BY o.expiresAt
            """)
    List<String> findExpiredPendingOrderNos(@Param("now") java.time.Instant now,
                                            @Param("status") OrderStatus status,
                                            org.springframework.data.domain.Pageable pageable);
}
