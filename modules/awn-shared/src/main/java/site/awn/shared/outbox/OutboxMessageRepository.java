package site.awn.shared.outbox;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Outbox 读写入口。分发查询（到期 PENDING 扫描、租约认领）在 Phase 04 Worker 中补充。
 */
public interface OutboxMessageRepository extends JpaRepository<OutboxMessage, Long> {

    long countByEventTypeAndAggregateId(String eventType, String aggregateId);
}
