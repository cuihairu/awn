package site.awn.intelligence.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * 原始观察：采集即落库（含 raw payload），标准化失败也可溯源重放。
 * 质量检查不通过 → REJECTED，绝不进入价格历史。
 */
@Entity
@Table(name = "raw_observations", schema = "intelligence")
public class RawObservation {

    /** 标准化版本：检查规则变更时递增 */
    public static final String NORMALIZATION_VERSION = "1";

    /** 未来时间容忍偏差：观察到入库允许的时钟偏差 */
    static final java.time.Duration FUTURE_SKEW = java.time.Duration.ofMinutes(5);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    @Column(name = "source_event_ref", nullable = false)
    private String sourceEventRef;

    @Column(name = "source_product_ref", nullable = false)
    private String sourceProductRef;

    @Column(name = "observed_at", nullable = false)
    private Instant observedAt;

    @Column(name = "price")
    private Long price;

    @Column(name = "currency")
    private String currency;

    @Column(name = "availability_label")
    private String availabilityLabel;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload", nullable = false, columnDefinition = "jsonb")
    private String rawPayload;

    @Column(name = "normalization_version", nullable = false)
    private String normalizationVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "quality_status", nullable = false)
    private QualityStatus qualityStatus;

    @Column(name = "reject_reason")
    private String rejectReason;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    protected RawObservation() {
    }

    public RawObservation(Long sourceId, String sourceEventRef, String sourceProductRef,
                          Instant observedAt, Long price, String currency,
                          String availabilityLabel, String rawPayload) {
        this.sourceId = sourceId;
        this.sourceEventRef = sourceEventRef;
        this.sourceProductRef = sourceProductRef;
        this.observedAt = observedAt;
        this.price = price;
        this.currency = currency;
        this.availabilityLabel = availabilityLabel;
        this.rawPayload = rawPayload;
        this.normalizationVersion = NORMALIZATION_VERSION;
        this.qualityStatus = QualityStatus.PENDING;
    }

    /**
     * 质量检查：价格必须为正、币种三位码、观察时间不得明显超前。
     * 通过返回标准化结果；不通过标记 REJECTED 并返回空。
     */
    public java.util.Optional<NormalizedObservation> normalize(DataSource source) {
        if (qualityStatus != QualityStatus.PENDING) {
            throw new IllegalStateException("observation already processed: " + qualityStatus);
        }
        String problem = qualityProblem();
        if (problem != null) {
            this.qualityStatus = QualityStatus.REJECTED;
            this.rejectReason = problem;
            return java.util.Optional.empty();
        }
        this.qualityStatus = QualityStatus.OK;
        NormalizedObservation normalized = NormalizedObservation.of(this, source,
                normalizeProductCode(sourceProductRef));
        return java.util.Optional.of(normalized);
    }

    private String qualityProblem() {
        if (price == null || price <= 0) {
            return "price must be positive";
        }
        if (currency == null || currency.length() != 3) {
            return "currency must be a 3-letter code";
        }
        if (observedAt != null && observedAt.isAfter(Instant.now().plus(FUTURE_SKEW))) {
            return "observed_at is too far in the future";
        }
        return null;
    }

    /** 来源商品引用 → 平台商品编码的标准化（统一小写、去前缀） */
    private String normalizeProductCode(String ref) {
        String code = ref == null ? "" : ref.trim().toLowerCase();
        if (code.startsWith("ref-")) {
            code = code.substring("ref-".length());
        }
        return code;
    }

    public Long getId() {
        return id;
    }

    public Long getSourceId() {
        return sourceId;
    }

    public String getSourceEventRef() {
        return sourceEventRef;
    }

    public String getSourceProductRef() {
        return sourceProductRef;
    }

    public Instant getObservedAt() {
        return observedAt;
    }

    public Long getPrice() {
        return price;
    }

    public String getCurrency() {
        return currency;
    }

    public String getAvailabilityLabel() {
        return availabilityLabel;
    }

    public String getRawPayload() {
        return rawPayload;
    }

    public String getNormalizationVersion() {
        return normalizationVersion;
    }

    public QualityStatus getQualityStatus() {
        return qualityStatus;
    }

    public String getRejectReason() {
        return rejectReason;
    }
}
