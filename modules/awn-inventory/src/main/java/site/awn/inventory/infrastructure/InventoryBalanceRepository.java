package site.awn.inventory.infrastructure;

import site.awn.inventory.domain.InventoryBalance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 库存余额的唯一写入路径：三条原子条件 UPDATE。
 * 这是防超卖的第一道防线（CHECK 约束是最后一道，见 V2__inventory_tables.sql）。
 * 禁止新增任何绕过条件谓词的余额写语句；updated_at 由触发器维护。
 */
public interface InventoryBalanceRepository extends JpaRepository<InventoryBalance, Long> {

    /** 预留：可用量充足时增加 reserved_quantity，返回 0 表示库存不足 */
    @Modifying
    @Query(value = """
            UPDATE inventory_balance
               SET reserved_quantity = reserved_quantity + :quantity,
                   version = version + 1
             WHERE inventory_pool_id = :poolId
               AND total_quantity - reserved_quantity - sold_quantity >= :quantity
            """, nativeQuery = true)
    int reserve(@Param("poolId") long poolId, @Param("quantity") int quantity);

    /** 确认：预留转已售；守卫 reserved_quantity >= :quantity 兜底 */
    @Modifying
    @Query(value = """
            UPDATE inventory_balance
               SET sold_quantity = sold_quantity + :quantity,
                   reserved_quantity = reserved_quantity - :quantity,
                   version = version + 1
             WHERE inventory_pool_id = :poolId
               AND reserved_quantity >= :quantity
            """, nativeQuery = true)
    int confirm(@Param("poolId") long poolId, @Param("quantity") int quantity);

    /** 释放：归还预留；守卫 reserved_quantity >= :quantity 兜底 */
    @Modifying
    @Query(value = """
            UPDATE inventory_balance
               SET reserved_quantity = reserved_quantity - :quantity,
                   version = version + 1
             WHERE inventory_pool_id = :poolId
               AND reserved_quantity >= :quantity
            """, nativeQuery = true)
    int release(@Param("poolId") long poolId, @Param("quantity") int quantity);
}
