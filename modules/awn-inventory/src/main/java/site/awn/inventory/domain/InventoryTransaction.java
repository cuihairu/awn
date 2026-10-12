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
 * 库存流水：每次计数器变更的不可变台账。
 * 余额、预留与流水的三方一致是对账（Phase 04）的基础。
 */
@Entity
@Table(name = "inventory_transaction")
public class InventoryTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long inventoryPoolId;

    /** 触发本次变更的预留；ADJUST 类型可为空 */
    private Long reservationId;

    @Enumerated(EnumType.STRING)
    private LedgerType type;

    /** 本条流水的票数（恒为正，方向由 type 决定） */
    private int quantity;

    /** 审计列：由数据库 DEFAULT 维护，实体只读 */
    @Column(insertable = false, updatable = false)
    private Instant createdAt;

    public Long getInventoryPoolId() {
        return inventoryPoolId;
    }

    public void setInventoryPoolId(Long inventoryPoolId) {
        this.inventoryPoolId = inventoryPoolId;
    }

    public Long getReservationId() {
        return reservationId;
    }

    public void setReservationId(Long reservationId) {
        this.reservationId = reservationId;
    }

    public LedgerType getType() {
        return type;
    }

    public void setType(LedgerType type) {
        this.type = type;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
