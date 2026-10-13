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

    /**
     * Outbox 插入（原生 SQL，ADR-0007）：与业务数据同事务写入；
     * 审计列（next_attempt_at/created_at）由数据库 DEFAULT 填充。
     */
    @Modifying
    @Query(value = """
            INSERT INTO outbox_messages (aggregate_type, aggregate_id, event_type, payload, status, attempts)
            VALUES (:aggregateType, :aggregateId, :eventType, CAST(:payload AS jsonb), 'PENDING', 0)
            """, nativeQuery = true)
    void insertPending(@Param("aggregateType") String aggregateType,
                       @Param("aggregateId") String aggregateId,
                       @Param("eventType") String eventType,
                       @Param("payload") String payload);

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
