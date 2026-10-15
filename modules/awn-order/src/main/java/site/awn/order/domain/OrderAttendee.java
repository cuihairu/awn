package site.awn.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 观演人实名（一票一证，V25__order_attendees.sql）：
 * 逐票绑定，创建后不提供修改路径（与订单明细快照同一不可变口径）。
 * {@code sessionId} 为下单行的场次快照冗余，承载「同场次同证件至多一张」的唯一索引。
 */
@Entity
@Table(name = "order_attendees")
public class OrderAttendee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    /** 下单行场次快照（可为 NULL：未声明场次的行不参与场次级证件唯一约束） */
    @Column(name = "session_id")
    private String sessionId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "id_no", nullable = false)
    private String idNo;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected OrderAttendee() {
    }

    public static OrderAttendee attach(Long itemId, String sessionId, String name, String idNo) {
        return new OrderAttendee(itemId, sessionId, name, idNo);
    }

    private OrderAttendee(Long itemId, String sessionId, String name, String idNo) {
        this.itemId = itemId;
        this.sessionId = sessionId;
        this.name = name;
        this.idNo = idNo;
    }

    public Long getId() {
        return id;
    }

    public Long getItemId() {
        return itemId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getName() {
        return name;
    }

    public String getIdNo() {
        return idNo;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
