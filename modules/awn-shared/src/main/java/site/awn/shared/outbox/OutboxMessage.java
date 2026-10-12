package site.awn.shared.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * 交易关键事件 Outbox：与业务数据同事务写入（docs/architecture/async-outbox.md）。
 * 事件由 Worker 扫描投递；进程重启不丢，重试上限后转 FAILED 并告警（Phase 04）。
 */
@Entity
@Table(name = "outbox_messages")
public class OutboxMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 事件来源聚合类型，如 ORDER */
    private String aggregateType;

    /** 聚合标识，如订单号 */
    private String aggregateId;

    /** 事件类型，如 ORDER_CREATED */
    private String eventType;

    /** 事件载荷（JSON 文本，落库为 JSONB） */
    @JdbcTypeCode(SqlTypes.JSON)
    private String payload;

    private String status;

    private int attempts;

    @Column(insertable = false, updatable = false)
    private Instant nextAttemptAt;

    /** 审计列：由数据库 DEFAULT 维护，实体只读 */
    @Column(insertable = false, updatable = false)
    private Instant createdAt;

    @Column(insertable = false, updatable = false)
    private Instant processedAt;

    public static OutboxMessage pending(String aggregateType, String aggregateId, String eventType, String payload) {
        OutboxMessage message = new OutboxMessage();
        message.aggregateType = aggregateType;
        message.aggregateId = aggregateId;
        message.eventType = eventType;
        message.payload = payload;
        message.status = "PENDING";
        message.attempts = 0;
        return message;
    }

    public Long getId() {
        return id;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPayload() {
        return payload;
    }

    public String getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }
}
