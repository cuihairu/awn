package site.awn.inventory.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import site.awn.inventory.domain.InventoryTransaction;
import site.awn.inventory.domain.LedgerType;

import java.util.List;

public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, Long> {

    /** 对账用：按池汇总 ADJUST 流水（建池 bootstrap + 运营调整，方向由符号携带）→ 期望 total */
    @Query("""
            SELECT t.inventoryPoolId, SUM(t.quantity)
              FROM InventoryTransaction t
             WHERE t.type = :type
             GROUP BY t.inventoryPoolId
           """)
    List<Object[]> sumQuantitiesByType(@Param("type") LedgerType type);
}
