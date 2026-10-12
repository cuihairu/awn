package site.awn.inventory.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.awn.inventory.domain.InventoryConflictException;
import site.awn.inventory.domain.InventoryReservation;
import site.awn.inventory.domain.InventoryTransaction;
import site.awn.inventory.domain.LedgerType;
import site.awn.inventory.domain.ReservationStatus;
import site.awn.inventory.infrastructure.InventoryBalanceRepository;
import site.awn.inventory.infrastructure.InventoryReservationRepository;
import site.awn.inventory.infrastructure.InventoryTransactionRepository;

/**
 * 预留确认与释放。
 * 竞态裁决规则（docs/architecture/inventory-consistency.md）：
 * 1. 先做带守卫的预留状态迁移，只有赢者（受影响行数 = 1）才能改余额；
 * 2. 重复确认 / 重复释放按幂等重放处理（返回 false，不重复改余额）；
 * 3. 确认已释放（或反之）抛 InventoryConflictException，进入异常处理流程，禁止静默吞掉。
 */
@Service
public class ReservationLifecycleService {

    private final InventoryReservationRepository reservationRepository;
    private final InventoryBalanceRepository balanceRepository;
    private final InventoryTransactionRepository transactionRepository;

    public ReservationLifecycleService(InventoryReservationRepository reservationRepository,
                                       InventoryBalanceRepository balanceRepository,
                                       InventoryTransactionRepository transactionRepository) {
        this.reservationRepository = reservationRepository;
        this.balanceRepository = balanceRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional
    public boolean confirm(String reservationNo) {
        int moved = reservationRepository.transition(reservationNo, ReservationStatus.ACTIVE, ReservationStatus.CONFIRMED);
        var reservation = load(reservationNo);
        if (moved == 1) {
            applyBalance(reservation, LedgerType.CONFIRM);
            return true;
        }
        return switch (reservation.getStatus()) {
            case CONFIRMED -> false; // 幂等重放
            case RELEASED -> throw new InventoryConflictException(
                    "cannot confirm released reservation: " + reservationNo);
            case ACTIVE -> throw new IllegalStateException("transition lost unexpectedly: " + reservationNo);
        };
    }

    @Transactional
    public boolean release(String reservationNo) {
        int moved = reservationRepository.transition(reservationNo, ReservationStatus.ACTIVE, ReservationStatus.RELEASED);
        var reservation = load(reservationNo);
        if (moved == 1) {
            applyBalance(reservation, LedgerType.RELEASE);
            return true;
        }
        return switch (reservation.getStatus()) {
            case RELEASED -> false; // 幂等重放：重复释放不重复回补库存
            case CONFIRMED -> throw new InventoryConflictException(
                    "cannot release confirmed reservation: " + reservationNo);
            case ACTIVE -> throw new IllegalStateException("transition lost unexpectedly: " + reservationNo);
        };
    }

    private InventoryReservation load(String reservationNo) {
        return reservationRepository.findByReservationNo(reservationNo)
                .orElseThrow(() -> new InventoryConflictException("reservation not found: " + reservationNo));
    }

    private void applyBalance(InventoryReservation reservation, LedgerType type) {
        long poolId = reservation.getInventoryPoolId();
        int quantity = reservation.getQuantity();
        int updated = type == LedgerType.CONFIRM
                ? balanceRepository.confirm(poolId, quantity)
                : balanceRepository.release(poolId, quantity);
        if (updated == 0) {
            // 守卫谓词兜底未命中：说明余额与预留状态已不一致，必须暴露而不是掩盖
            throw new IllegalStateException("inventory balance guard missed for reservation: "
                    + reservation.getReservationNo());
        }

        var ledger = new InventoryTransaction();
        ledger.setInventoryPoolId(poolId);
        ledger.setReservationId(reservation.getId());
        ledger.setType(type);
        ledger.setQuantity(quantity);
        transactionRepository.save(ledger);
    }
}
