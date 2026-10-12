package site.awn.inventory.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.awn.inventory.api.CreatePoolCommand;
import site.awn.inventory.api.PoolView;
import site.awn.inventory.api.ReservationView;
import site.awn.inventory.api.ReserveInventoryCommand;
import site.awn.inventory.domain.InventoryBalance;
import site.awn.inventory.domain.InventoryConflictException;
import site.awn.inventory.domain.InventoryPool;
import site.awn.inventory.domain.InventoryReservation;
import site.awn.inventory.domain.InventoryTransaction;
import site.awn.inventory.domain.InsufficientInventoryException;
import site.awn.inventory.domain.LedgerType;
import site.awn.inventory.domain.PoolNotFoundException;
import site.awn.inventory.domain.ReservationStatus;
import site.awn.inventory.infrastructure.InventoryBalanceRepository;
import site.awn.inventory.infrastructure.InventoryPoolRepository;
import site.awn.inventory.infrastructure.InventoryReservationRepository;
import site.awn.inventory.infrastructure.InventoryTransactionRepository;

import java.time.Instant;
import java.util.UUID;

/**
 * 预留 / 建池用例。预留事务 = 原子条件 UPDATE + 预留记录 + 流水，整体提交或回滚。
 * 订单链路（Phase 03）在自己的事务中调用本服务，实现"订单 + 预留 + Outbox 同事务"。
 */
@Service
public class InventoryWriteService {

    private final InventoryPoolRepository poolRepository;
    private final InventoryBalanceRepository balanceRepository;
    private final InventoryReservationRepository reservationRepository;
    private final InventoryTransactionRepository transactionRepository;

    public InventoryWriteService(InventoryPoolRepository poolRepository,
                                 InventoryBalanceRepository balanceRepository,
                                 InventoryReservationRepository reservationRepository,
                                 InventoryTransactionRepository transactionRepository) {
        this.poolRepository = poolRepository;
        this.balanceRepository = balanceRepository;
        this.reservationRepository = reservationRepository;
        this.transactionRepository = transactionRepository;
    }

    /**
     * 运营库存调整：正数扩容、负数缩容，写 ADJUST 流水（方向由符号携带）。
     * 调整后总量必须仍满足 reserved + sold <= total；原因必填（审计由组合根记录）。
     */
    @Transactional
    public void adjustPool(String poolCode, int delta, String reason, String actor) {
        if (delta == 0) {
            throw new IllegalArgumentException("adjustment delta must not be zero");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("adjustment reason is required");
        }
        var pool = poolRepository.findByPoolCode(poolCode)
                .orElseThrow(() -> new PoolNotFoundException(poolCode));
        var balance = balanceRepository.findById(pool.getId())
                .orElseThrow(() -> new IllegalStateException("balance missing for pool: " + poolCode));
        int newTotal = balance.getTotalQuantity() + delta;
        if (newTotal < balance.getReservedQuantity() + balance.getSoldQuantity()) {
            throw new InventoryConflictException(
                    "adjustment would break reserved + sold <= total for pool: " + poolCode);
        }
        balance.setTotalQuantity(newTotal);
        balanceRepository.save(balance);

        var ledger = new InventoryTransaction();
        ledger.setInventoryPoolId(pool.getId());
        ledger.setType(LedgerType.ADJUST);
        ledger.setQuantity(delta);
        transactionRepository.save(ledger);
    }

    @Transactional
    public PoolView createPool(CreatePoolCommand command) {
        if (command.totalQuantity() < 0) {
            throw new IllegalArgumentException("totalQuantity must be >= 0");
        }
        if (poolRepository.existsByPoolCode(command.poolCode())) {
            throw new IllegalArgumentException("pool code already exists: " + command.poolCode());
        }
        if (poolRepository.existsByProductIdAndSessionId(command.productId(), command.sessionId())) {
            throw new IllegalArgumentException("pool already exists for product/session: "
                    + command.productId() + "/" + command.sessionId());
        }

        var pool = new InventoryPool();
        pool.setPoolCode(command.poolCode());
        pool.setProductId(command.productId());
        pool.setSessionId(command.sessionId());
        pool = poolRepository.save(pool);

        var balance = new InventoryBalance();
        balance.setInventoryPoolId(pool.getId());
        balance.setTotalQuantity(command.totalQuantity());
        balanceRepository.save(balance);

        return new PoolView(pool.getPoolCode());
    }

    @Transactional
    public ReservationView reserve(ReserveInventoryCommand command) {
        var pool = poolRepository.findByPoolCode(command.poolCode())
                .orElseThrow(() -> new PoolNotFoundException(command.poolCode()));

        // 第一道防线：原子条件 UPDATE，未命中即库存不足
        if (balanceRepository.reserve(pool.getId(), command.quantity()) == 0) {
            throw new InsufficientInventoryException(command.poolCode());
        }

        var reservation = new InventoryReservation();
        reservation.setReservationNo(UUID.randomUUID().toString());
        reservation.setInventoryPoolId(pool.getId());
        reservation.setOrderRef(command.orderRef());
        reservation.setQuantity(command.quantity());
        reservation.setStatus(ReservationStatus.ACTIVE);
        reservation.setExpiresAt(command.ttl() == null ? null : Instant.now().plus(command.ttl()));
        reservation = reservationRepository.save(reservation);

        var ledger = new InventoryTransaction();
        ledger.setInventoryPoolId(pool.getId());
        ledger.setReservationId(reservation.getId());
        ledger.setType(LedgerType.RESERVE);
        ledger.setQuantity(command.quantity());
        transactionRepository.save(ledger);

        return new ReservationView(reservation.getReservationNo(), pool.getPoolCode(),
                reservation.getQuantity(), reservation.getExpiresAt());
    }
}
