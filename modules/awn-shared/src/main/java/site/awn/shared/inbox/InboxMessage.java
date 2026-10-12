package site.awn.shared.inbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Inbox 消费去重记录：consumer + message_id 唯一。
 * 与消费者业务副作用同事务写入，保证同一事件对同一消费者只生效一次
 * （docs/architecture/async-outbox.md）。
 */
@Entity
@Table(name = "inbox_messages")
public class InboxMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 消费者标识，如 order */
    @Column(nullable = false)
    private String consumer;

    /** 来源 Outbox 消息 ID */
    @Column(name = "message_id", nullable = false)
    private Long messageId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "processed_at", insertable = false, updatable = false)
    private Instant processedAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected InboxMessage() {
    }

    public InboxMessage(String consumer, Long messageId, String eventType) {
        this.consumer = consumer;
        this.messageId = messageId;
        this.eventType = eventType;
    }

    public Long getId() {
        return id;
    }

    public String getConsumer() {
        return consumer;
    }

    public Long getMessageId() {
        return messageId;
    }

    public String getEventType() {
        return eventType;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
