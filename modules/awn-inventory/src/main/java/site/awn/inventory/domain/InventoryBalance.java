package site.awn.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 库存余额计数器：库存模块内唯一的可变库存状态。
 * 所有变更只允许走原子条件 UPDATE（Infrastructure 层 InventoryBalanceRepository），
 * 禁止通过本实体 setter 修改计数——那是绕过防超卖防线的路径。
 * 不变量：available = total - reserved - sold；reserved + sold <= total。
 */
@Entity
@Table(name = "inventory_balance")
public class InventoryBalance {

    /** 与 inventory_pool 一对一，主键即池 id */
    @Id
    private Long inventoryPoolId;

    private int totalQuantity;

    private int reservedQuantity;

    private int soldQuantity;

    /** 乐观版本号，仅由原子 UPDATE 维护 */
    private long version;

    /** 审计列：由数据库触发器 awn_set_updated_at() 维护，实体只读 */
    @Column(insertable = false, updatable = false)
    private Instant updatedAt;

    public int available() {
        return totalQuantity - reservedQuantity - soldQuantity;
    }

    public Long getInventoryPoolId() {
        return inventoryPoolId;
    }

    public void setInventoryPoolId(Long inventoryPoolId) {
        this.inventoryPoolId = inventoryPoolId;
    }

    public int getTotalQuantity() {
        return totalQuantity;
    }

    public void setTotalQuantity(int totalQuantity) {
        this.totalQuantity = totalQuantity;
    }

    public int getReservedQuantity() {
        return reservedQuantity;
    }

    public void setReservedQuantity(int reservedQuantity) {
        this.reservedQuantity = reservedQuantity;
    }

    public int getSoldQuantity() {
        return soldQuantity;
    }

    public void setSoldQuantity(int soldQuantity) {
        this.soldQuantity = soldQuantity;
    }

    public long getVersion() {
        return version;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
