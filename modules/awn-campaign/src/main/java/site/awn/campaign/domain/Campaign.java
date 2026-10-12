package site.awn.campaign.domain;

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
 * 专场（活动聚合页）。
 * 专场通过"商品引用"组织交易商品，自身不拥有库存和订单实现。
 */
@Entity
@Table(name = "campaigns")
public class Campaign {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "campaign_code", nullable = false, unique = true)
    private String campaignCode;

    @Column(nullable = false)
    private String title;

    @Column
    private String subtitle;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CampaignStatus status = CampaignStatus.DRAFT;

    @Column(name = "current_revision_id")
    private Long currentRevisionId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config_snapshot", columnDefinition = "jsonb")
    private String configSnapshot;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    protected Campaign() {
    }

    public Campaign(String campaignCode, String title, String subtitle) {
        this.campaignCode = campaignCode;
        this.title = title;
        this.subtitle = subtitle;
        this.status = CampaignStatus.DRAFT;
    }

    public Long getId() {
        return id;
    }

    public String getCampaignCode() {
        return campaignCode;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public void setSubtitle(String subtitle) {
        this.subtitle = subtitle;
    }

    public CampaignStatus getStatus() {
        return status;
    }

    public Long getCurrentRevisionId() {
        return currentRevisionId;
    }

    public String getConfigSnapshot() {
        return configSnapshot;
    }

    public void setConfigSnapshot(String configSnapshot) {
        this.configSnapshot = configSnapshot;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void publish(Long revisionId) {
        this.status = CampaignStatus.PUBLISHED;
        this.currentRevisionId = revisionId;
    }

    public void unpublish() {
        this.status = CampaignStatus.UNPUBLISHED;
    }

    public void archive() {
        this.status = CampaignStatus.ARCHIVED;
    }

    public void markValidated() {
        this.status = CampaignStatus.VALIDATED;
    }
}
