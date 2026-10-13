package site.awn.intelligence.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** 情报来源：数据分类与可信度分级（1 最高）。 */
@Entity
@Table(name = "data_sources", schema = "intelligence")
public class DataSource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_no", nullable = false, unique = true)
    private String sourceNo;

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false)
    private SourceKind kind;

    @Column(name = "trust_tier", nullable = false)
    private int trustTier;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    protected DataSource() {
    }

    public DataSource(String sourceNo, String name, SourceKind kind, int trustTier) {
        if (trustTier < 1 || trustTier > 3) {
            throw new IllegalArgumentException("trust tier must be 1..3: " + trustTier);
        }
        this.sourceNo = sourceNo;
        this.name = name;
        this.kind = kind;
        this.trustTier = trustTier;
        this.enabled = true;
    }

    public Long getId() {
        return id;
    }

    public String getSourceNo() {
        return sourceNo;
    }

    public String getName() {
        return name;
    }

    public SourceKind getKind() {
        return kind;
    }

    public int getTrustTier() {
        return trustTier;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void disable() {
        this.enabled = false;
    }

    public void enable() {
        this.enabled = true;
    }
}
