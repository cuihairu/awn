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
 * 支付单。金额为最小货币单位整数。
 * 支付成功即使回调到达多次，也只产生一次有效业务确认（条件更新守卫）。
 */
@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_no", nullable = false, unique = true)
    private String paymentNo;

    @Column(name = "order_no", nullable = false)
    private String orderNo;

    @Column(nullable = false)
    private String channel;

    @Column(name = "channel_txn_no")
    private String channelTxnNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status = PaymentStatus.CREATED;

    @Column(nullable = false)
    private String currency;

    @Column(nullable = false)
    private long amount;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    protected Payment() {
    }

    public Payment(String paymentNo, String orderNo, String channel,
                   String currency, long amount) {
        this.paymentNo = paymentNo;
        this.orderNo = orderNo;
        this.channel = channel;
        this.currency = currency;
        this.amount = amount;
        this.status = PaymentStatus.CREATED;
    }

    public Long getId() {
        return id;
    }

    public String getPaymentNo() {
        return paymentNo;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public String getChannel() {
        return channel;
    }

    public String getChannelTxnNo() {
        return channelTxnNo;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getCurrency() {
        return currency;
    }

    public long getAmount() {
        return amount;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /** 渠道受理后回填渠道交易号：CREATED → PENDING */
    public void markPending(String channelTxnNo) {
        ensureTransition(PaymentStatus.PENDING);
        if (this.channelTxnNo != null && !this.channelTxnNo.equals(channelTxnNo)) {
            throw new PaymentConflictException("channel txn no already bound: " + this.channelTxnNo);
        }
        this.channelTxnNo = channelTxnNo;
        this.status = PaymentStatus.PENDING;
    }

    /**
     * 标记支付成功。仅允许从 CREATED/PENDING/UNKNOWN 迁入；
     * 并发重复回调由数据库条件更新守卫（见 PaymentRepository.markSucceeded）。
     */
    public void markSucceeded() {
        ensureTransition(PaymentStatus.SUCCEEDED);
        this.status = PaymentStatus.SUCCEEDED;
        this.paidAt = Instant.now();
    }

    public void markFailed() {
        ensureTransition(PaymentStatus.FAILED);
        this.status = PaymentStatus.FAILED;
    }

    /**
     * 支付结果待核实：回调成功但订单已超时释放等竞态场景。
     * 未明确确认支付失败前不盲目确认（docs/architecture/payment-state-machine.md）。
     */
    public void markUnknown() {
        ensureTransition(PaymentStatus.UNKNOWN);
        this.status = PaymentStatus.UNKNOWN;
    }

    public void applyRefundState(PaymentStatus target) {
        ensureTransition(target);
        this.status = target;
    }

    private void ensureTransition(PaymentStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new PaymentConflictException(
                    "payment transition not allowed: " + status + " -> " + target);
        }
    }
}
