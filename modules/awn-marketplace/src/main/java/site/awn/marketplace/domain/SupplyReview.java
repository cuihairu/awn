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
 * 供货审核：卖家申请供货，平台审核通过后方可获得
 * 「平台保证可交付」资质（挂牌 platform_guaranteed 的唯一来源）。
 */
@Entity
@Table(name = "supply_reviews")
public class SupplyReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "review_no", nullable = false, unique = true)
    private String reviewNo;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(name = "product_code", nullable = false)
    private String productCode;

    @Column(name = "session_code")
    private String sessionCode;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    /** 已消耗额度：platform_guaranteed 挂牌时累加，上限为 quantity */
    @Column(name = "consumed_quantity", nullable = false)
    private int consumedQuantity;

    @Column(name = "unit_price", nullable = false)
    private long unitPrice;

    /** 供货凭证：仅白名单存储域（应用层校验） */
    @Column(name = "evidence_url")
    private String evidenceUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SupplyReviewStatus status;

    @Column(name = "reviewer")
    private String reviewer;

    @Column(name = "reject_reason")
    private String rejectReason;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    protected SupplyReview() {
    }

    public SupplyReview(String reviewNo, Long sellerId, String productCode, String sessionCode,
                        int quantity, long unitPrice, String evidenceUrl) {
        this.reviewNo = reviewNo;
        this.sellerId = sellerId;
        this.productCode = productCode;
        this.sessionCode = sessionCode;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.evidenceUrl = evidenceUrl;
        this.status = SupplyReviewStatus.PENDING;
    }

    /** 审核通过：PENDING → APPROVED */
    public void approve(String reviewer) {
        ensureDecidable();
        this.status = SupplyReviewStatus.APPROVED;
        this.reviewer = reviewer;
        this.decidedAt = Instant.now();
    }

    /** 审核拒绝：PENDING → REJECTED（拒绝的供货不得挂牌） */
    public void reject(String reviewer, String reason) {
        ensureDecidable();
        this.status = SupplyReviewStatus.REJECTED;
        this.reviewer = reviewer;
        this.rejectReason = reason;
        this.decidedAt = Instant.now();
    }

    public boolean isApproved() {
        return status == SupplyReviewStatus.APPROVED;
    }

    private void ensureDecidable() {
        if (status != SupplyReviewStatus.PENDING) {
            throw new IllegalStateException("supply review already decided: " + status);
        }
    }

    public Long getId() {
        return id;
    }

    public String getReviewNo() {
        return reviewNo;
    }

    public Long getSellerId() {
        return sellerId;
    }

    public String getProductCode() {
        return productCode;
    }

    public String getSessionCode() {
        return sessionCode;
    }

    public int getQuantity() {
        return quantity;
    }

    public int getConsumedQuantity() {
        return consumedQuantity;
    }

    public long getUnitPrice() {
        return unitPrice;
    }

    public String getEvidenceUrl() {
        return evidenceUrl;
    }

    public SupplyReviewStatus getStatus() {
        return status;
    }

    public String getReviewer() {
        return reviewer;
    }

    public String getRejectReason() {
        return rejectReason;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }
}
