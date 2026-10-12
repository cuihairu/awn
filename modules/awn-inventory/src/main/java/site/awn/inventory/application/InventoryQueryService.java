package site.awn.inventory.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.awn.inventory.api.BalanceView;
import site.awn.inventory.domain.PoolNotFoundException;
import site.awn.inventory.infrastructure.InventoryBalanceRepository;
import site.awn.inventory.infrastructure.InventoryPoolRepository;

import java.util.Optional;

/**
 * 库存只读查询。跨端可售展示值为缓存口径，下单时仍以后端原子预留为准。
 */
@Service
public class InventoryQueryService {

    private final InventoryPoolRepository poolRepository;
    private final InventoryBalanceRepository balanceRepository;

    public InventoryQueryService(InventoryPoolRepository poolRepository,
                                 InventoryBalanceRepository balanceRepository) {
        this.poolRepository = poolRepository;
        this.balanceRepository = balanceRepository;
    }

    @Transactional(readOnly = true)
    public BalanceView balanceOf(String poolCode) {
        var pool = poolRepository.findByPoolCode(poolCode)
                .orElseThrow(() -> new PoolNotFoundException(poolCode));
        var balance = balanceRepository.findById(pool.getId())
                .orElseThrow(() -> new IllegalStateException("balance missing for pool: " + poolCode));
        return new BalanceView(poolCode, balance.getTotalQuantity(), balance.getReservedQuantity(),
                balance.getSoldQuantity(), balance.available(), balance.getVersion());
    }

    /**
     * 按商品查可售摘要（商品在该场次仅有一个池；无池返回空，由调用方按未开售展示）。
     */
    @Transactional(readOnly = true)
    public Optional<BalanceView> balanceOfProduct(String productId) {
        var pools = poolRepository.findByProductId(productId);
        if (pools.isEmpty()) {
            return Optional.empty();
        }
        if (pools.size() > 1) {
            throw new IllegalStateException("multiple pools for product: " + productId);
        }
        return Optional.of(balanceOf(pools.get(0).getPoolCode()));
    }
}
