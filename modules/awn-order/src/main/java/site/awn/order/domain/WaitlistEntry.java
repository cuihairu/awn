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
 * 候补条目（排队 / 候补席位）。
 *
 * <p>排队只登记意愿：条目不占库存，也不预生成订单。递补由 Worker 任务驱动，
 * 抢占条目（WAITING → PROMOTED）与生成待支付订单在同一事务内，
 * 预留失败整条回滚，条目保持 WAITING 等下一轮。
 *
 * <p>票档信息（商品名 / 单价 / 场次）是加入候补时的快照：排队期间商品改价不回溯
 * 历史条目，递补建单按快照重算应付金额。
 */
@Entity
@Table(name = "waitlist_entries")
public class WaitlistEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 对外稳定的候补编号 */
    @Column(name = "waitlist_no", nullable = false, unique = true)
    private String waitlistNo;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "session_id", nullable = false)
    private String sessionId;

    @Column(name = "pool_code", nullable = false)
    private String poolCode;

    @Column(name = "product_id", nullable = false)
    private String productId;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false)
    private long unitPrice;

    @Column(nullable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WaitlistStatus status = WaitlistStatus.WAITING;

    /** 递补生成的订单号；排队中为 null */
    @Column(name = "order_no")
    private String orderNo;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    protected WaitlistEntry() {
    }

    public WaitlistEntry(String waitlistNo, Long userId, String sessionId, String poolCode,
                         String productId, String productName, int quantity,
                         long unitPrice, String currency) {
        this.waitlistNo = waitlistNo;
        this.userId = userId;
        this.sessionId = sessionId;
        this.poolCode = poolCode;
        this.productId = productId;
        this.productName = productName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.currency = currency;
        this.status = WaitlistStatus.WAITING;
    }

    public Long getId() {
        return id;
    }

    public String getWaitlistNo() {
        return waitlistNo;
    }

    public Long getUserId() {
        return userId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getPoolCode() {
        return poolCode;
    }

    public String getProductId() {
        return productId;
    }

    public String getProductName() {
        return productName;
    }

    public int getQuantity() {
        return quantity;
    }

    public long getUnitPrice() {
        return unitPrice;
    }

    public String getCurrency() {
        return currency;
    }

    public WaitlistStatus getStatus() {
        return status;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}