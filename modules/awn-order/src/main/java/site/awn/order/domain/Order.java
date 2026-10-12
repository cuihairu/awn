package site.awn.order.domain;

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
 * 订单聚合根。金额一律最小货币单位整数；明细快照在 {@link OrderItem}。
 * 状态迁移只允许走带守卫的条件 UPDATE（OrderRepository.transition），
 * 禁止通过本实体 setter 修改 status。
 */
@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 对外稳定的订单号 */
    private String orderNo;

    /** 幂等键；数据库唯一约束是并发同键的最后防线 */
    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    /** 请求指纹（SHA-256）：同键同请求重放返回原结果，同键不同请求拒绝 */
    private String requestHash;

    private Long userId;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    private String currency;

    private long totalAmount;

    private long discountAmount;

    private long feeAmount;

    private long payAmount;

    /** 支付截止时间；Worker 的订单超时任务（Phase 03 后续）据此释放库存 */
    private Instant expiresAt;

    /** 审计列：由数据库 DEFAULT 维护，实体只读 */
    @Column(insertable = false, updatable = false)
    private Instant createdAt;

    /** 审计列：由数据库触发器 awn_set_updated_at() 维护，实体只读 */
    @Column(insertable = false, updatable = false)
    private Instant updatedAt;

    public Long getId() {
        return id;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public void setRequestHash(String requestHash) {
        this.requestHash = requestHash;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public long getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(long totalAmount) {
        this.totalAmount = totalAmount;
    }

    public long getDiscountAmount() {
        return discountAmount;
    }

    public void setDiscountAmount(long discountAmount) {
        this.discountAmount = discountAmount;
    }

    public long getFeeAmount() {
        return feeAmount;
    }

    public void setFeeAmount(long feeAmount) {
        this.feeAmount = feeAmount;
    }

    public long getPayAmount() {
        return payAmount;
    }

    public void setPayAmount(long payAmount) {
        this.payAmount = payAmount;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
