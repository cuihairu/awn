package site.awn.order.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import site.awn.order.domain.WaitlistEntry;
import site.awn.order.domain.WaitlistStatus;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface WaitlistRepository extends JpaRepository<WaitlistEntry, Long> {

    Optional<WaitlistEntry> findByWaitlistNo(String waitlistNo);

    /** 同用户同池占用位次判重（排队中与已递补都算占位） */
    boolean existsByUserIdAndPoolCodeAndStatusIn(Long userId, String poolCode,
                                                 Collection<WaitlistStatus> statuses);

    /** 我的候补列表（加入时间倒序） */
    List<WaitlistEntry> findByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    /** FIFO 顺序（created_at, id）取指定状态的条目 */
    List<WaitlistEntry> findByStatusOrderByCreatedAtAscIdAsc(WaitlistStatus status);

    /** 同池指定状态的 FIFO 队列（位次计算与递补取队首共用） */
    List<WaitlistEntry> findByPoolCodeAndStatusOrderByCreatedAtAscIdAsc(String poolCode,
                                                                        WaitlistStatus status);

    /** 有等待中条目的池（递补扫描按池推进） */
    @Query("SELECT DISTINCT w.poolCode FROM WaitlistEntry w WHERE w.status = :status ORDER BY w.poolCode")
    List<String> findPoolCodesByStatus(@Param("status") WaitlistStatus status);

    /**
     * 状态机守卫迁移：仅当当前状态等于 from 时迁移到 to。
     * 返回 0 表示状态已变化（竞态或非法迁移），调用方必须重读后显式处理，禁止盲目写状态。
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE WaitlistEntry w
               SET w.status = :to
             WHERE w.waitlistNo = :waitlistNo
               AND w.status = :from
            """)
    int transition(@Param("waitlistNo") String waitlistNo,
                   @Param("from") WaitlistStatus from,
                   @Param("to") WaitlistStatus to);

    /**
     * 绑定递补订单号：仅当席位仍为 PROMOTED 且尚未绑单时生效，
     * 避免并发递补或重放给同一席位绑上第二笔订单。
     * 返回 0 表示状态已变化（竞态或重放），调用方必须重读后显式处理。
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE WaitlistEntry w
               SET w.orderNo = :orderNo
             WHERE w.waitlistNo = :waitlistNo
               AND w.status = :status
               AND w.orderNo IS NULL
            """)
    int bindOrder(@Param("waitlistNo") String waitlistNo,
                  @Param("status") WaitlistStatus status,
                  @Param("orderNo") String orderNo);

    /**
     * 加入候补串行化锁：同一用户同池的判重 + 落库在同一事务内串行执行，
     * 先到的提交后，后到的在锁内看到已占用席位，走冲突分支。
     */
    @Query(value = "SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtext(:key))) AS lock", nativeQuery = true)
    void lockPoolJoin(@Param("key") String key);
}
