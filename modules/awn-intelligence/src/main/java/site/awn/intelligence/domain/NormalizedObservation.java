package site.awn.intelligence.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** 标准化观察：质量检查通过的观察，携带来源可信度快照 */
@Entity
@Table(name = "normalized_observations", schema = "intelligence")
public class NormalizedObservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "raw_observation_id", nullable = false, unique = true)
    private Long rawObservationId;

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

    protected NormalizedObservation() {
    }

    static NormalizedObservation of(RawObservation raw, DataSource source, String productCode) {
        NormalizedObservation n = new NormalizedObservation();
        n.rawObservationId = raw.getId();
        n.sourceId = source.getId();
        n.productCode = productCode;
        n.sessionCode = null;
        n.price = raw.getPrice();
        n.currency = raw.getCurrency();
        n.availability = raw.getAvailabilityLabel();
        n.observedAt = raw.getObservedAt();
        n.trustTier = source.getTrustTier();
        return n;
    }

    public Long getId() {
        return id;
    }

    public Long getRawObservationId() {
        return rawObservationId;
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
