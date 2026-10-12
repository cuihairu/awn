package site.awn.campaign.domain;

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
 * 不可变的专场发布版本。
 * 发布时生成配置快照，已发布版本不会因草稿编辑被覆盖。
 */
@Entity
@Table(name = "campaign_revisions")
public class CampaignRevision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "campaign_id", nullable = false)
    private Long campaignId;

    @Column(name = "revision_number", nullable = false)
    private Integer revisionNumber;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config_snapshot", nullable = false, columnDefinition = "jsonb")
    private String configSnapshot;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected CampaignRevision() {
    }

    public CampaignRevision(Long campaignId, Integer revisionNumber, String configSnapshot) {
        this.campaignId = campaignId;
        this.revisionNumber = revisionNumber;
        this.configSnapshot = configSnapshot;
    }

    public Long getId() {
        return id;
    }

    public Long getCampaignId() {
        return campaignId;
    }

    public Integer getRevisionNumber() {
        return revisionNumber;
    }

    public String getConfigSnapshot() {
        return configSnapshot;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
