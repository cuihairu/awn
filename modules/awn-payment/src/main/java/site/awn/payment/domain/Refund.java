package site.awn.payment.domain;

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
 * 退款单：独立退款编号、渠道退款编号、幂等键。
 * 累计成功退款金额不得超过支付金额；并发重复退款由幂等键唯一约束与额度校验共同守卫。
 */
@Entity
@Table(name = "refunds")
public class Refund {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "refund_no", nullable = false, unique = true)
    private String refundNo;

    @Column(name = "payment_no", nullable = false)
    private String paymentNo;

    @Column(name = "order_no", nullable = false)
    private String orderNo;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    @Column(name = "channel_refund_no")
    private String channelRefundNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RefundStatus status = RefundStatus.CREATED;

    @Column(nullable = false)
    private String currency;

    @Column(nullable = false)
    private long amount;

    @Column
    private String reason;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    protected Refund() {
    }

    public Refund(String refundNo, String paymentNo, String orderNo, String idempotencyKey,
                  String currency, long amount, String reason) {
        this.refundNo = refundNo;
        this.paymentNo = paymentNo;
        this.orderNo = orderNo;
        this.idempotencyKey = idempotencyKey;
        this.currency = currency;
        this.amount = amount;
        this.reason = reason;
        this.status = RefundStatus.CREATED;
    }

    public Long getId() {
        return id;
    }

    public String getRefundNo() {
        return refundNo;
    }

    public String getPaymentNo() {
        return paymentNo;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getChannelRefundNo() {
        return channelRefundNo;
    }

    public RefundStatus getStatus() {
        return status;
    }

    public String getCurrency() {
        return currency;
    }

    public long getAmount() {
        return amount;
    }

    public String getReason() {
        return reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /** 渠道受理：CREATED → PENDING，绑定渠道退款编号 */
    public void markPending(String channelRefundNo) {
        ensureTransition(RefundStatus.PENDING);
        this.channelRefundNo = channelRefundNo;
        this.status = RefundStatus.PENDING;
    }

    public void markSucceeded() {
        ensureTransition(RefundStatus.SUCCEEDED);
        this.status = RefundStatus.SUCCEEDED;
    }

    public void markFailed() {
        ensureTransition(RefundStatus.FAILED);
        this.status = RefundStatus.FAILED;
    }

    public void markUnknown() {
        ensureTransition(RefundStatus.UNKNOWN);
        this.status = RefundStatus.UNKNOWN;
    }

    private void ensureTransition(RefundStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new RefundConflictException(
                    "refund transition not allowed: " + status + " -> " + target);
        }
    }
}
