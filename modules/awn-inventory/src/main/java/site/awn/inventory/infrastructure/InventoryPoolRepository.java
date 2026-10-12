package site.awn.inventory.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import site.awn.inventory.domain.InventoryPool;

import java.util.List;
import java.util.Optional;

public interface InventoryPoolRepository extends JpaRepository<InventoryPool, Long> {

    Optional<InventoryPool> findByPoolCode(String poolCode);

    List<InventoryPool> findByProductId(String productId);

    boolean existsByPoolCode(String poolCode);

    boolean existsByProductIdAndSessionId(String productId, String sessionId);
}
