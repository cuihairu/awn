package site.awn.shared.alert;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** 运营告警记录：可触发、可确认 */
@Entity
@Table(name = "alerts")
public class AlertRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false)
    private AlertCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false)
    private AlertSeverity severity;

    @Column(name = "message", nullable = false)
    private String message;

    @Column(name = "reference")
    private String reference;

    @Column(name = "acknowledged", nullable = false)
    private boolean acknowledged;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected AlertRecord() {
    }

    public AlertRecord(AlertCategory category, AlertSeverity severity, String message, String reference) {
        this.category = category;
        this.severity = severity;
        this.message = message;
        this.reference = reference;
        this.acknowledged = false;
    }

    public void acknowledge() {
        this.acknowledged = true;
    }

    public Long getId() {
        return id;
    }

    public AlertCategory getCategory() {
        return category;
    }

    public AlertSeverity getSeverity() {
        return severity;
    }

    public String getMessage() {
        return message;
    }

    public String getReference() {
        return reference;
    }

    public boolean isAcknowledged() {
        return acknowledged;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
