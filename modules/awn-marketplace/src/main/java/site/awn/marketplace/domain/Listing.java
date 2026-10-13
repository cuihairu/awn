package site.awn.marketplace.domain;

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
 * 挂牌：独立票品的售卖入口。
 * 平台边界（docs/architecture/module-boundaries.md）：
 * platform_guaranteed 只能来自 APPROVED 的供货审核——
 * 未审核供货绝不能被标为「平台保证可交付」。
 */
@Entity
@Table(name = "listings")
public class Listing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "listing_no", nullable = false, unique = true)
    private String listingNo;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(name = "supply_review_id", nullable = false)
    private Long supplyReviewId;

    @Column(name = "product_code", nullable = false)
    private String productCode;

    @Column(name = "session_code")
    private String sessionCode;

    @Column(name = "unit_price", nullable = false)
    private long unitPrice;

    @Column(name = "platform_guaranteed", nullable = false)
    private boolean platformGuaranteed;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ListingStatus status;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    protected Listing() {
    }

    /**
     * 挂牌工厂：platformGuaranteed=true 要求供货审核已通过；
     * 已拒绝的供货不得挂牌；未审核供货可挂（卖家自担），但绝不是平台保证。
     */
    public static Listing create(String listingNo, Long sellerId, SupplyReview review,
                                 String productCode, String sessionCode, long unitPrice,
                                 boolean platformGuaranteed) {
        if (review.getStatus() == SupplyReviewStatus.REJECTED) {
            throw new MarketplaceConflictException(
                    "rejected supply review cannot be listed: " + review.getReviewNo());
        }
        if (platformGuaranteed && !review.isApproved()) {
            throw new MarketplaceConflictException(
                    "platform guarantee requires approved supply review: " + review.getReviewNo()
                            + " is " + review.getStatus());
        }
        return new Listing(listingNo, sellerId, review.getId(), productCode, sessionCode,
                unitPrice, platformGuaranteed);
    }

    private Listing(String listingNo, Long sellerId, Long supplyReviewId,
                    String productCode, String sessionCode, long unitPrice, boolean platformGuaranteed) {
        this.listingNo = listingNo;
        this.sellerId = sellerId;
        this.supplyReviewId = supplyReviewId;
        this.productCode = productCode;
        this.sessionCode = sessionCode;
        this.unitPrice = unitPrice;
        this.platformGuaranteed = platformGuaranteed;
        this.status = ListingStatus.LISTED;
    }

    public void delist() {
        if (status == ListingStatus.SOLD) {
            throw new MarketplaceConflictException("sold listing cannot be delisted: " + listingNo);
        }
        // 有待支付订单在途的挂牌不可下架：否则买家下单成功但卖家撤单
        if (status == ListingStatus.RESERVED) {
            throw new MarketplaceConflictException(
                    "reserved listing cannot be delisted while an order is pending: " + listingNo);
        }
        this.status = ListingStatus.DELISTED;
    }

    public void markReserved() {
        if (status != ListingStatus.LISTED) {
            throw new MarketplaceConflictException("reserve not allowed from status " + status);
        }
        this.status = ListingStatus.RESERVED;
    }

    public void markSold() {
        if (status != ListingStatus.RESERVED && status != ListingStatus.LISTED) {
            throw new MarketplaceConflictException("sold not allowed from status " + status);
        }
        this.status = ListingStatus.SOLD;
    }

    public Long getId() {
        return id;
    }

    public String getListingNo() {
        return listingNo;
    }

    public Long getSellerId() {
        return sellerId;
    }

    public Long getSupplyReviewId() {
        return supplyReviewId;
    }

    public String getProductCode() {
        return productCode;
    }

    public String getSessionCode() {
        return sessionCode;
    }

    public long getUnitPrice() {
        return unitPrice;
    }

    public boolean isPlatformGuaranteed() {
        return platformGuaranteed;
    }

    public ListingStatus getStatus() {
        return status;
    }
}
