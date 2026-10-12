package site.awn.shared.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

/**
 * Outbox 读写入口。分发查询（到期 PENDING 扫描、租约认领）由 OutboxDispatchService 使用。
 */
public interface OutboxMessageRepository extends JpaRepository<OutboxMessage, Long> {

    long countByEventTypeAndAggregateId(String eventType, String aggregateId);

    /** 认领候选：到期 PENDING + 租约过期的 PROCESSING（崩溃残留），行锁直到认领事务提交 */
    @Query(value = """
            SELECT id FROM outbox_messages
            WHERE (status = 'PENDING' AND next_attempt_at <= :now)
               OR (status = 'PROCESSING' AND next_attempt_at <= :leaseDeadline)
            ORDER BY next_attempt_at
            LIMIT :batch
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<Long> lockDueIds(@Param("now") Instant now,
                          @Param("leaseDeadline") Instant leaseDeadline,
                          @Param("batch") int batch);

    @Modifying
    @Query("UPDATE OutboxMessage m SET m.status = 'PROCESSING' WHERE m.id IN :ids")
    int claim(@Param("ids") List<Long> ids);
}
