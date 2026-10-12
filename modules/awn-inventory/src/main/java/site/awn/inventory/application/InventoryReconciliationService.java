package site.awn.inventory.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.awn.inventory.api.ReconciliationReport;
import site.awn.inventory.api.ReconciliationViolation;
import site.awn.inventory.domain.InventoryBalance;
import site.awn.inventory.domain.InventoryPool;
import site.awn.inventory.domain.ReservationStatus;
import site.awn.inventory.infrastructure.InventoryBalanceRepository;
import site.awn.inventory.infrastructure.InventoryPoolRepository;
import site.awn.inventory.infrastructure.InventoryReservationRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 库存对账（Worker 调用）：预留记录 ↔ 余额计数器逐池核对。
 *
 * <p>期望值推导：reserved = Σ ACTIVE 预留量，sold = Σ CONFIRMED 预留量。
 * 只报告不修复——自动改写计数器会掩盖真正的缺陷（丢流水、重复迁移、越权更新），
 * 差异必须先被看见。</p>
 */
@Service
public class InventoryReconciliationService {

    private final InventoryBalanceRepository balances;
    private final InventoryPoolRepository pools;
    private final InventoryReservationRepository reservations;

    InventoryReconciliationService(InventoryBalanceRepository balances,
                                   InventoryPoolRepository pools,
                                   InventoryReservationRepository reservations) {
        this.balances = balances;
        this.pools = pools;
        this.reservations = reservations;
    }

    @Transactional(readOnly = true)
    public ReconciliationReport reconcile() {
        Map<Long, Long> expectedReserved = sumsByStatus(ReservationStatus.ACTIVE);
        Map<Long, Long> expectedSold = sumsByStatus(ReservationStatus.CONFIRMED);

        List<ReconciliationViolation> violations = new ArrayList<>();
        List<InventoryBalance> all = balances.findAll();
        for (InventoryBalance balance : all) {
            Long poolId = balance.getInventoryPoolId();
            String poolCode = pools.findById(poolId).map(InventoryPool::getPoolCode).orElse("pool-" + poolId);
            long expectedReservedQuantity = expectedReserved.getOrDefault(poolId, 0L);
            long expectedSoldQuantity = expectedSold.getOrDefault(poolId, 0L);
            if (expectedReservedQuantity != balance.getReservedQuantity()) {
                violations.add(new ReconciliationViolation(poolCode, "RESERVED_MISMATCH",
                        expectedReservedQuantity, balance.getReservedQuantity()));
            }
            if (expectedSoldQuantity != balance.getSoldQuantity()) {
                violations.add(new ReconciliationViolation(poolCode, "SOLD_MISMATCH",
                        expectedSoldQuantity, balance.getSoldQuantity()));
            }
        }
        return new ReconciliationReport(all.size(), violations);
    }

    private Map<Long, Long> sumsByStatus(ReservationStatus status) {
        Map<Long, Long> sums = new HashMap<>();
        for (Object[] row : reservations.sumQuantitiesByStatus(status)) {
            sums.put((Long) row[0], ((Number) row[1]).longValue());
        }
        return sums;
    }
}
