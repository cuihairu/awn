package site.awn.promotion.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** 券实例：一张发给某用户的券，状态 UNUSED → USED（终态） */
@Entity
@Table(name = "coupons")
public class Coupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "coupon_no", nullable = false, unique = true)
    private String couponNo;

    @Column(name = "template_id", nullable = false)
    private Long templateId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CouponStatus status;

    @Column(name = "issued_at", insertable = false, updatable = false)
    private Instant issuedAt;

    @Column(name = "used_at")
    private Instant usedAt;

    protected Coupon() {
    }

    public Coupon(String couponNo, Long templateId, Long userId) {
        this.couponNo = couponNo;
        this.templateId = templateId;
        this.userId = userId;
        this.status = CouponStatus.UNUSED;
    }

    public Long getId() {
        return id;
    }

    public String getCouponNo() {
        return couponNo;
    }

    public Long getTemplateId() {
        return templateId;
    }

    public Long getUserId() {
        return userId;
    }

    public CouponStatus getStatus() {
        return status;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    /** 核销：UNUSED → USED；由仓储守卫迁移调用 */
    public void markUsed(Instant usedAt) {
        this.status = CouponStatus.USED;
        this.usedAt = usedAt;
    }
}
