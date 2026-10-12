package site.awn.intelligence.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** 价格历史：每条价格可溯源（来源、更新时间、可信度） */
@Entity
@Table(name = "price_history", schema = "intelligence")
public class PriceHistoryEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    @Column(name = "product_code", nullable = false)
    private String productCode;

    @Column(name = "session_code")
    private String sessionCode;

    @Column(name = "price", nullable = false)
    private long price;

    @Column(name = "currency", nullable = false)
    private String currency;

    @Column(name = "availability")
    private String availability;

    @Column(name = "observed_at", nullable = false)
    private Instant observedAt;

    @Column(name = "trust_tier", nullable = false)
    private int trustTier;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected PriceHistoryEntry() {
    }

    public PriceHistoryEntry(NormalizedObservation observation, String sessionCode) {
        this.sourceId = observation.getSourceId();
        this.productCode = observation.getProductCode();
        this.sessionCode = sessionCode;
        this.price = observation.getPrice();
        this.currency = observation.getCurrency();
        this.availability = observation.getAvailability();
        this.observedAt = observation.getObservedAt();
        this.trustTier = observation.getTrustTier();
    }

    public Long getId() {
        return id;
    }

    public Long getSourceId() {
        return sourceId;
    }

    public String getProductCode() {
        return productCode;
    }

    public String getSessionCode() {
        return sessionCode;
    }

    public long getPrice() {
        return price;
    }

    public String getCurrency() {
        return currency;
    }

    public String getAvailability() {
        return availability;
    }

    public Instant getObservedAt() {
        return observedAt;
    }

    public int getTrustTier() {
        return trustTier;
    }
}
