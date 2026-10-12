package site.awn.inventory.application;

import org.springframework.stereotype.Service;
import site.awn.inventory.api.BalanceView;
import site.awn.inventory.api.CreatePoolCommand;
import site.awn.inventory.api.InventoryFacade;
import site.awn.inventory.api.PoolView;
import site.awn.inventory.api.ReconciliationReport;
import site.awn.inventory.api.ReservationView;
import site.awn.inventory.api.ReserveInventoryCommand;

/**
 * InventoryFacade 的模块内实现：仅做用例委派，不含业务规则。
 */
@Service
public class InventoryFacadeImpl implements InventoryFacade {

    private final InventoryWriteService writeService;
    private final ReservationLifecycleService lifecycleService;
    private final InventoryQueryService queryService;
    private final InventoryReconciliationService reconciliationService;

    public InventoryFacadeImpl(InventoryWriteService writeService,
                               ReservationLifecycleService lifecycleService,
                               InventoryQueryService queryService,
                               InventoryReconciliationService reconciliationService) {
        this.writeService = writeService;
        this.lifecycleService = lifecycleService;
        this.queryService = queryService;
        this.reconciliationService = reconciliationService;
    }

    @Override
    public PoolView createPool(CreatePoolCommand command) {
        return writeService.createPool(command);
    }

    @Override
    public ReservationView reserve(ReserveInventoryCommand command) {
        return writeService.reserve(command);
    }

    @Override
    public boolean confirm(String reservationNo) {
        return lifecycleService.confirm(reservationNo);
    }

    @Override
    public boolean release(String reservationNo) {
        return lifecycleService.release(reservationNo);
    }

    @Override
    public BalanceView balanceOf(String poolCode) {
        return queryService.balanceOf(poolCode);
    }

    @Override
    public ReconciliationReport reconcile() {
        return reconciliationService.reconcile();
    }
}
