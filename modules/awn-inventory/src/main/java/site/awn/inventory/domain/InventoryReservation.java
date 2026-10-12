package site.awn.inventory.domain;

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
 * 库存预留记录：一次成功原子扣减的业务凭证。
 * 状态迁移（ACTIVE → CONFIRMED / RELEASED）由应用层带守卫的 UPDATE 完成，
 * 并在同一事务内同步修改 {@link InventoryBalance}。
 */
@Entity
@Table(name = "inventory_reservation")
public class InventoryReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 全局唯一预留号（对幂等重放与对账可见） */
    private String reservationNo;

    private Long inventoryPoolId;

    /** 关联订单的引用；订单模块接入（Phase 03）后由订单链路写入 */
    private String orderRef;

    private int quantity;

    @Enumerated(EnumType.STRING)
    private ReservationStatus status;

    /** 超时释放期限；由 Worker 的 order-timeout / inventory-expiry 任务消费 */
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

    public String getReservationNo() {
        return reservationNo;
    }

    public void setReservationNo(String reservationNo) {
        this.reservationNo = reservationNo;
    }

    public Long getInventoryPoolId() {
        return inventoryPoolId;
    }

    public void setInventoryPoolId(Long inventoryPoolId) {
        this.inventoryPoolId = inventoryPoolId;
    }

    public String getOrderRef() {
        return orderRef;
    }

    public void setOrderRef(String orderRef) {
        this.orderRef = orderRef;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public void setStatus(ReservationStatus status) {
        this.status = status;
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
