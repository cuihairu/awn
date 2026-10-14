package site.awn.inventory.application;

import org.springframework.stereotype.Service;
import site.awn.inventory.api.BalanceView;
import site.awn.inventory.api.CreatePoolCommand;
import site.awn.inventory.api.CreateSeatsCommand;
import site.awn.inventory.api.InventoryFacade;
import site.awn.inventory.api.PoolView;
import site.awn.inventory.api.ReconciliationReport;
import site.awn.inventory.api.ReservationExpiryResult;
import site.awn.inventory.api.ReservationView;
import site.awn.inventory.api.ReserveInventoryCommand;
import site.awn.inventory.api.SeatView;
import site.awn.shared.alert.AlertCategory;
import site.awn.shared.alert.AlertRecorder;
import site.awn.shared.alert.AlertSeverity;

/**
 * InventoryFacade 的模块内实现：用例委派 + 对账差异告警。
 */
@Service
public class InventoryFacadeImpl implements InventoryFacade {

    private final InventoryWriteService writeService;
    private final ReservationLifecycleService lifecycleService;
    private final InventoryQueryService queryService;
    private final InventoryReconciliationService reconciliationService;
    private final ReservationExpiryService expiryService;
    private final PlatformSeatService seatService;
    private final AlertRecorder alerts;

    public InventoryFacadeImpl(InventoryWriteService writeService,
                               ReservationLifecycleService lifecycleService,
                               InventoryQueryService queryService,
                               InventoryReconciliationService reconciliationService,
                               ReservationExpiryService expiryService,
                               PlatformSeatService seatService,
                               AlertRecorder alerts) {
        this.writeService = writeService;
        this.lifecycleService = lifecycleService;
        this.queryService = queryService;
        this.reconciliationService = reconciliationService;
        this.expiryService = expiryService;
        this.seatService = seatService;
        this.alerts = alerts;
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
    public java.util.Optional<BalanceView> balanceOfProduct(String productId) {
        return queryService.balanceOfProduct(productId);
    }

    @Override
    public PoolView poolByProductId(String productId) {
        return queryService.poolByProductId(productId);
    }

    @Override
    public void adjustPool(String poolCode, int delta, String reason, String actor) {
        writeService.adjustPool(poolCode, delta, reason, actor);
    }

    @Override
    public java.util.List<BalanceView> balances() {
        return queryService.balances();
    }

    @Override
    public ReservationExpiryResult releaseExpiredReservations(java.time.Instant now) {
        return expiryService.releaseExpiredReservations(now);
    }

    @Override
    public java.util.List<SeatView> createSeats(CreateSeatsCommand command) {
        return seatService.createSeats(command);
    }

    @Override
    public SeatView seat(String seatNo) {
        return seatService.seat(seatNo);
    }

    @Override
    public java.util.List<SeatView> seatsOfProduct(String productCode) {
        return seatService.seatsOfProduct(productCode);
    }

    @Override
    public java.util.List<SeatView> seatsOfSession(String sessionCode) {
        return seatService.seatsOfSession(sessionCode);
    }

    @Override
    public SeatView reserveSeat(String seatNo, String orderNo) {
        return seatService.reserveSeat(seatNo, orderNo);
    }

    @Override
    public SeatView confirmSeat(String seatNo) {
        return seatService.confirmSeat(seatNo);
    }

    @Override
    public SeatView releaseSeat(String seatNo) {
        return seatService.releaseSeat(seatNo);
    }

    @Override
    public SeatView deliverSeat(String seatNo) {
        return seatService.deliverSeat(seatNo);
    }

    @Override
    public SeatView invalidateSeat(String seatNo) {
        return seatService.invalidateSeat(seatNo);
    }

    @Override
    public ReconciliationReport reconcile() {
        ReconciliationReport report = reconciliationService.reconcile();
        // 库存异常必须可告警：账实不符逐条落 INVENTORY 告警（不自动修复）
        for (var violation : report.violations()) {
            alerts.raise(AlertCategory.INVENTORY, AlertSeverity.CRITICAL,
                    "inventory " + violation.kind() + " for pool " + violation.poolCode()
                            + ": expected=" + violation.expected() + " actual=" + violation.actual(),
                    violation.poolCode());
        }
        return report;
    }
}
