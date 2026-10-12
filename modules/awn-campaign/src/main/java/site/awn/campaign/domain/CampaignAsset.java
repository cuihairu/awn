package site.awn.campaign.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 专场所使用的图片、视频等资源。
 */
@Entity
@Table(name = "campaign_assets")
public class CampaignAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "campaign_id", nullable = false)
    private Long campaignId;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_type", nullable = false)
    private AssetType assetType;

    @Column(nullable = false)
    private String url;

    @Column(name = "alt_text")
    private String altText;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected CampaignAsset() {
    }

    public CampaignAsset(Long campaignId, AssetType assetType, String url, String altText, Integer displayOrder) {
        this.campaignId = campaignId;
        this.assetType = assetType;
        this.url = url;
        this.altText = altText;
        this.displayOrder = displayOrder;
    }

    public Long getId() {
        return id;
    }

    public Long getCampaignId() {
        return campaignId;
    }

    public AssetType getAssetType() {
        return assetType;
    }

    public String getUrl() {
        return url;
    }

    public String getAltText() {
        return altText;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public enum AssetType {
        IMAGE, VIDEO, BANNER, THUMBNAIL
    }
}
