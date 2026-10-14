package site.awn.inventory.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.awn.catalog.api.CatalogFacade;
import site.awn.inventory.api.CreateSeatsCommand;
import site.awn.inventory.api.SeatView;
import site.awn.inventory.domain.InventoryConflictException;
import site.awn.inventory.domain.PlatformSeat;
import site.awn.inventory.domain.SeatNotFoundException;
import site.awn.inventory.domain.SeatStatus;
import site.awn.inventory.infrastructure.PlatformSeatRepository;

import java.util.List;
import java.util.UUID;

/**
 * 平台座位用例：铺座 / 逐座状态迁移。
 * 迁移与预留生命周期同口径：先做守卫条件 UPDATE（并发抢座只放行一单，0 行即败），
 * 再加载实体给出精确报错——不在更新前加载，避免一级缓存读到旧状态。
 * 座位是独立模型，不复用挂牌票品，也不与数量型库存池互指。
 */
@Service
public class PlatformSeatService {

    private final PlatformSeatRepository seatRepository;
    private final CatalogFacade catalog;

    public PlatformSeatService(PlatformSeatRepository seatRepository, CatalogFacade catalog) {
        this.seatRepository = seatRepository;
        this.catalog = catalog;
    }

    /** 铺座（管理操作）：商品编码经 catalog 校验存在；同场次同位置重复即拒绝（DB 唯一约束兜底） */
    @Transactional
    public List<SeatView> createSeats(CreateSeatsCommand command) {
        catalog.product(command.productCode());
        for (CreateSeatsCommand.SeatPosition position : command.positions()) {
            if (position.section() == null || position.section().isBlank()
                    || position.row() == null || position.row().isBlank()
                    || position.seat() == null || position.seat().isBlank()) {
                throw new IllegalArgumentException("seat position fields are required");
            }
            if (seatRepository.existsBySessionCodeAndSectionNameAndRowLabelAndSeatLabel(
                    command.sessionCode(), position.section(), position.row(), position.seat())) {
                throw new IllegalArgumentException(
                        "seat position already exists: " + command.sessionCode() + "/"
                                + position.section() + "/" + position.row() + "/" + position.seat());
            }
        }
        return command.positions().stream()
                .map(position -> seatRepository.save(new PlatformSeat("SEAT-" + UUID.randomUUID(),
                        command.productCode(), command.sessionCode(),
                        position.section(), position.row(), position.seat())))
                .map(PlatformSeatService::toView)
                .toList();
    }

    @Transactional(readOnly = true)
    public SeatView seat(String seatNo) {
        return seatRepository.findBySeatNo(seatNo)
                .map(PlatformSeatService::toView)
                .orElseThrow(() -> new SeatNotFoundException(seatNo));
    }

    @Transactional(readOnly = true)
    public List<SeatView> seatsOfProduct(String productCode) {
        return seatRepository.findByProductCodeOrderById(productCode).stream()
                .map(PlatformSeatService::toView)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SeatView> seatsOfSession(String sessionCode) {
        return seatRepository.findBySessionCodeOrderById(sessionCode).stream()
                .map(PlatformSeatService::toView)
                .toList();
    }

    /** 预留座位（IN_STOCK → RESERVED 并登记订单号）：并发抢座守卫裁决，败者抛冲突 */
    @Transactional
    public SeatView reserveSeat(String seatNo, String orderNo) {
        if (orderNo == null || orderNo.isBlank()) {
            throw new IllegalArgumentException("orderNo is required");
        }
        int moved = seatRepository.reserve(seatNo, orderNo);
        return requireMoved(moved, seatNo, SeatStatus.RESERVED);
    }

    /**
     * 为订单预留座位（选座下单链路）：商品一致性在此裁决——
     * 座位是独立模型，行商品写错时不得静默占座（买 A 商品的价占 B 商品的座）。
     * 位置属性创建后不变，只读校验与守卫预留之间无竞态（状态竞态由守卫迁移裁决）。
     */
    @Transactional
    public SeatView reserveSeatForOrder(String seatNo, String orderNo, String productCode) {
        SeatView seat = seat(seatNo);
        if (!seat.productCode().equals(productCode)) {
            throw new InventoryConflictException(
                    "seat " + seatNo + " belongs to product " + seat.productCode()
                            + ", not " + productCode);
        }
        return reserveSeat(seatNo, orderNo);
    }

    /** 确认出票（RESERVED → SOLD）：订单号保持，供对账与交付追溯 */
    @Transactional
    public SeatView confirmSeat(String seatNo) {
        int moved = seatRepository.confirm(seatNo);
        return requireMoved(moved, seatNo, SeatStatus.SOLD);
    }

    /** 释放座位（RESERVED → IN_STOCK 并清空订单号）：取消 / 超时链路调用 */
    @Transactional
    public SeatView releaseSeat(String seatNo) {
        int moved = seatRepository.release(seatNo);
        return requireMoved(moved, seatNo, SeatStatus.IN_STOCK);
    }

    /** 交付（SOLD → DELIVERED）：终态 */
    @Transactional
    public SeatView deliverSeat(String seatNo) {
        int moved = seatRepository.deliver(seatNo);
        return requireMoved(moved, seatNo, SeatStatus.DELIVERED);
    }

    /** 作废（IN_STOCK / RESERVED → INVALID）：坏座下架，终态 */
    @Transactional
    public SeatView invalidateSeat(String seatNo) {
        int moved = seatRepository.invalidate(seatNo);
        return requireMoved(moved, seatNo, SeatStatus.INVALID);
    }

    /**
     * 守卫 UPDATE 后置检查：赢者返回最新视图；败者按现状给出精确冲突
     * （非法迁移 vs 被并发抢先）。座位不存在时 load 直接抛 SeatNotFoundException。
     */
    private SeatView requireMoved(int moved, String seatNo, SeatStatus target) {
        var seat = seatRepository.findBySeatNo(seatNo)
                .orElseThrow(() -> new SeatNotFoundException(seatNo));
        if (moved == 1) {
            return toView(seat);
        }
        if (!seat.getStatus().canTransitionTo(target)) {
            throw new InventoryConflictException(
                    "seat transition not allowed: " + seat.getStatus() + " -> " + target
                            + " (seat " + seatNo + ")");
        }
        throw new InventoryConflictException(
                "seat state changed concurrently: " + seatNo + " (now " + seat.getStatus() + ")");
    }

    static SeatView toView(PlatformSeat seat) {
        return new SeatView(seat.getSeatNo(), seat.getProductCode(), seat.getSessionCode(),
                seat.getSectionName(), seat.getRowLabel(), seat.getSeatLabel(),
                seat.getOrderNo(), seat.getStatus());
    }
}
