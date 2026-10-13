package site.awn.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** 通知消息：一次通道投递的留痕（SENT / FAILED），不承载未决状态 */
@Entity
@Table(name = "notification_messages")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recipient_id", nullable = false)
    private Long recipientId;

    @Column(name = "channel", nullable = false)
    private String channel;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "body", nullable = false)
    private String body;

    @Column(name = "reference")
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private NotificationStatus status;

    @Column(name = "fail_reason")
    private String failReason;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected Notification() {
    }

    private Notification(long recipientId, String channel, String eventType,
                         String title, String body, String reference) {
        this.recipientId = recipientId;
        this.channel = channel;
        this.eventType = eventType;
        this.title = title;
        this.body = body;
        this.reference = reference;
    }

    public static Notification sent(long recipientId, String channel, String eventType,
                                    String title, String body, String reference) {
        Notification n = new Notification(recipientId, channel, eventType, title, body, reference);
        n.status = NotificationStatus.SENT;
        return n;
    }

    public static Notification failed(long recipientId, String channel, String eventType,
                                      String title, String body, String reference, String reason) {
        Notification n = new Notification(recipientId, channel, eventType, title, body, reference);
        n.status = NotificationStatus.FAILED;
        n.failReason = reason;
        return n;
    }

    public Long getId() {
        return id;
    }

    public Long getRecipientId() {
        return recipientId;
    }

    public String getChannel() {
        return channel;
    }

    public String getEventType() {
        return eventType;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public String getReference() {
        return reference;
    }

    public NotificationStatus getStatus() {
        return status;
    }

    public String getFailReason() {
        return failReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
