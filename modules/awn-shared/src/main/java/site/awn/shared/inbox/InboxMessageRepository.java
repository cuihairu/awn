package site.awn.shared.inbox;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Inbox 读写入口。consumer + message_id 数据库唯一约束兜底并发去重。
 */
public interface InboxMessageRepository extends JpaRepository<InboxMessage, Long> {

    boolean existsByConsumerAndMessageId(String consumer, Long messageId);
}
