package site.awn.promotion.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 优惠券模板：面额、有效期窗口、发放配额。
 * 配额消耗走守卫递增（CouponTemplateRepository.tryConsumeQuota），
 * CHECK 约束（issued_count <= total_quota）是并发下的最后一道防线。
 */
@Entity
@Table(name = "coupon_templates")
public class CouponTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "template_no", nullable = false, unique = true)
    private String templateNo;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "discount_kind", nullable = false)
    private String discountKind;

    @Column(name = "discount_value", nullable = false)
    private long discountValue;

    @Column(name = "min_spend", nullable = false)
    private long minSpend;

    @Column(name = "valid_from")
    private Instant validFrom;

    @Column(name = "valid_until")
    private Instant validUntil;

    @Column(name = "total_quota", nullable = false)
    private int totalQuota;

    @Column(name = "issued_count", nullable = false)
    private int issuedCount;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected CouponTemplate() {
    }

    public CouponTemplate(String templateNo, String title, String discountKind, long discountValue,
                          long minSpend, Instant validFrom, Instant validUntil, int totalQuota) {
        this.templateNo = templateNo;
        this.title = title;
        this.discountKind = discountKind;
        this.discountValue = discountValue;
        this.minSpend = minSpend;
        this.validFrom = validFrom;
        this.validUntil = validUntil;
        this.totalQuota = totalQuota;
        this.issuedCount = 0;
    }

    /** 当前是否在发放 / 核销窗口内（null 表示该侧无界） */
    public boolean acceptsAt(Instant now) {
        if (validFrom != null && now.isBefore(validFrom)) {
            return false;
        }
        return validUntil == null || !now.isAfter(validUntil);
    }

    public boolean hasQuota() {
        return issuedCount < totalQuota;
    }

    public Long getId() {
        return id;
    }

    public String getTemplateNo() {
        return templateNo;
    }

    public String getTitle() {
        return title;
    }

    public String getDiscountKind() {
        return discountKind;
    }

    public long getDiscountValue() {
        return discountValue;
    }

    public long getMinSpend() {
        return minSpend;
    }

    public Instant getValidFrom() {
        return validFrom;
    }

    public Instant getValidUntil() {
        return validUntil;
    }

    public int getTotalQuota() {
        return totalQuota;
    }

    public int getIssuedCount() {
        return issuedCount;
    }

    /** 由仓储守卫递增后同步实体状态（同一事务） */
    public void markIssued() {
        this.issuedCount++;
    }
}
