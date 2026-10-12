package site.awn.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 库存池：某商品在某场次的可售池定义。
 * 计数器（总 / 预留 / 已售）在 {@link InventoryBalance}，本实体只承载标识与归属。
 */
@Entity
@Table(name = "inventory_pool")
public class InventoryPool {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 对外稳定的池标识（跨模块引用、幂等日志用） */
    private String poolCode;

    private String productId;

    private String sessionId;

    /** 审计列：由数据库 DEFAULT 维护，实体只读 */
    @Column(insertable = false, updatable = false)
    private Instant createdAt;

    /** 审计列：由数据库触发器 awn_set_updated_at() 维护，实体只读 */
    @Column(insertable = false, updatable = false)
    private Instant updatedAt;

    public Long getId() {
        return id;
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

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
