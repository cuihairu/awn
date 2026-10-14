package site.awn.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 订单明细快照（docs/architecture/domain-model.md「订单快照」）：
 * 下单时的成交事实（商品名、票档、单价），创建后不可变——
 * 数据库触发器直接拒绝任何 UPDATE，应用层不提供任何修改路径。
 */
@Entity
@Table(name = "order_items")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long orderId;

    /** 库存池引用（下单重校验与释放走库存模块） */
    private String poolCode;

    private String productId;

    private String productNameSnapshot;

    private String sessionId;

    private String ticketCategorySnapshot;

    private int quantity;

    private long unitPrice;

    private long discountAmount;

    private long feeAmount;

    /** quantity * unitPrice - discountAmount + feeAmount，服务端计算 */
    private long lineTotal;

    /** 本行对应的库存预留号（数量型行；取消/超时释放/支付确认按行操作库存） */
    private String reservationNo;

    /** 挂牌号（凭证型行；null 表示数量型行） */
    private String listingNo;

    /** 挂牌的独立票品号（凭证型行；下单时 IN_STOCK → RESERVED） */
    private String unitNo;

    /** 座位号（座位型行；下单时 platform_seats IN_STOCK → RESERVED） */
    private String seatNo;

    /** 审计列：由数据库 DEFAULT 维护，实体只读 */
    @Column(insertable = false, updatable = false)
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public String getPoolCode() {
        return poolCode;
    }

    public void setPoolCode(String poolCode) {
        this.poolCode = poolCode;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public String getProductNameSnapshot() {
        return productNameSnapshot;
    }

    public void setProductNameSnapshot(String productNameSnapshot) {
        this.productNameSnapshot = productNameSnapshot;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getTicketCategorySnapshot() {
        return ticketCategorySnapshot;
    }

    public void setTicketCategorySnapshot(String ticketCategorySnapshot) {
        this.ticketCategorySnapshot = ticketCategorySnapshot;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public long getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(long unitPrice) {
        this.unitPrice = unitPrice;
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

    public long getLineTotal() {
        return lineTotal;
    }

    public void setLineTotal(long lineTotal) {
        this.lineTotal = lineTotal;
    }

    public String getReservationNo() {
        return reservationNo;
    }

    public void setReservationNo(String reservationNo) {
        this.reservationNo = reservationNo;
    }

    public String getListingNo() {
        return listingNo;
    }

    public void setListingNo(String listingNo) {
        this.listingNo = listingNo;
    }

    public String getUnitNo() {
        return unitNo;
    }

    public void setUnitNo(String unitNo) {
        this.unitNo = unitNo;
    }

    public String getSeatNo() {
        return seatNo;
    }

    public void setSeatNo(String seatNo) {
        this.seatNo = seatNo;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
