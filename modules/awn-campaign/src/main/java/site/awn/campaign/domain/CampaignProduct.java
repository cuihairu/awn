package site.awn.campaign.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 专场引用的商品及展示排序。
 * 专场只能引用商品，不能复制商品或虚构库存。
 */
@Entity
@Table(name = "campaign_products")
public class CampaignProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "campaign_id", nullable = false)
    private Long campaignId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected CampaignProduct() {
    }

    public CampaignProduct(Long campaignId, Long productId, Integer displayOrder) {
        this.campaignId = campaignId;
        this.productId = productId;
        this.displayOrder = displayOrder;
    }

    public Long getId() {
        return id;
    }

    public Long getCampaignId() {
        return campaignId;
    }

    public Long getProductId() {
        return productId;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
