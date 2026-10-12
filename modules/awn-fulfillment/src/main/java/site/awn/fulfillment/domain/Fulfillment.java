package site.awn.fulfillment.domain;

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
 * 履约单：一单一履约（order_no 唯一）。
 * PAYMENT_SUCCEEDED 事件创建为 PENDING_SELLER；完成必须经过
 * 卖家交付 + 平台核验。失败进 FAILED 并发 FULFILLMENT_FAILED 事件，
 * 进入异常流程而非静默吞掉。
 */
@Entity
@Table(name = "fulfillments")
public class Fulfillment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fulfillment_no", nullable = false, unique = true)
    private String fulfillmentNo;

    @Column(name = "order_no", nullable = false, unique = true)
    private String orderNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private FulfillmentStatus status;

    @Column(name = "fail_reason")
    private String failReason;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    protected Fulfillment() {
    }

    public Fulfillment(String fulfillmentNo, String orderNo) {
        this.fulfillmentNo = fulfillmentNo;
        this.orderNo = orderNo;
        this.status = FulfillmentStatus.PENDING_SELLER;
    }

    /** 卖家交付：进入平台核验（凭证 URL 白名单校验在应用层） */
    public void markDelivered() {
        ensureTransition(FulfillmentStatus.PENDING_VERIFICATION);
        this.status = FulfillmentStatus.PENDING_VERIFICATION;
    }

    /** 平台核验通过：交付完成 */
    public void verifyPassed() {
        ensureTransition(FulfillmentStatus.COMPLETED);
        this.status = FulfillmentStatus.COMPLETED;
    }

    /** 平台核验拒绝：履约失败，进入异常流程 */
    public void verifyRejected(String reason) {
        ensureTransition(FulfillmentStatus.FAILED);
        this.status = FulfillmentStatus.FAILED;
        this.failReason = reason;
    }

    /** 履约失败（卖家取消 / 超时 / 异常），进入异常流程 */
    public void markFailed(String reason) {
        ensureTransition(FulfillmentStatus.FAILED);
        this.status = FulfillmentStatus.FAILED;
        this.failReason = reason;
    }

    public void cancel(String reason) {
        ensureTransition(FulfillmentStatus.CANCELLED);
        this.status = FulfillmentStatus.CANCELLED;
        if (reason != null) {
            this.failReason = reason;
        }
    }

    private void ensureTransition(FulfillmentStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new FulfillmentConflictException(
                    "fulfillment transition not allowed: " + status + " -> " + target);
        }
    }

    public Long getId() {
        return id;
    }

    public String getFulfillmentNo() {
        return fulfillmentNo;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public FulfillmentStatus getStatus() {
        return status;
    }

    public String getFailReason() {
        return failReason;
    }
}
