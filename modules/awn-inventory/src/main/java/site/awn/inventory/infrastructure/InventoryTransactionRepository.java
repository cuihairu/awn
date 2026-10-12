package site.awn.inventory.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import site.awn.inventory.domain.InventoryTransaction;

public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, Long> {
}
