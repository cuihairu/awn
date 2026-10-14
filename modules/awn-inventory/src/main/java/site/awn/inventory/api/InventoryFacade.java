package site.awn.inventory.api;

import java.util.Optional;

/**
 * 库存模块对外唯一入口（跨模块只允许调用本接口，禁止引用内部实现）。
 * reserve / confirm / release 均在单数据库事务内完成"状态迁移 + 余额变更 + 流水"。
 */
public interface InventoryFacade {

    /** 创建库存池（管理操作，Phase 02 由商品链路调用） */
    PoolView createPool(CreatePoolCommand command);

    /** 原子预留：库存不足抛 InsufficientInventoryException */
    ReservationView reserve(ReserveInventoryCommand command);

    /** 确认预留（ACTIVE → CONFIRMED）：返回 true 表示本次完成迁移，false 表示幂等重放 */
    boolean confirm(String reservationNo);

    /** 释放预留（ACTIVE → RELEASED）：返回 true 表示本次完成迁移，false 表示幂等重放 */
    boolean release(String reservationNo);

    /** 余额视图（含可用量） */
    BalanceView balanceOf(String poolCode);

    /**
     * 按商品查可售摘要（公共商品接口的缓存展示口径，下单仍需原子预留重校验）。
     * 商品尚未建池时返回空。
     */
    Optional<BalanceView> balanceOfProduct(String productId);

    /**
     * 运营库存调整（契约 POST /api/v1/admin/inventory/adjustments）：
     * 正数扩容、负数缩容，写 ADJUST 流水。要求操作原因（审计记录由组合根负责）。
     * 调整后总量必须仍满足 reserved + sold <= total。
     */
    void adjustPool(String poolCode, int delta, String reason, String actor);

    /** 全部池的余额视图（运营库存中心） */
    java.util.List<BalanceView> balances();

    /**
     * 预留到期扫描（Worker 调用）：释放所有已过期的 ACTIVE 预留并回补库存。
     * 幂等由预留状态机守卫保证，重复执行不重复回补。
     */
    ReservationExpiryResult releaseExpiredReservations(java.time.Instant now);

    /** 按商品 ID 查找库存池（同一商品在同一场次只允许一个池） */
    PoolView poolByProductId(String productId);

    /**
     * Worker 库存对账：以预留记录（ACTIVE→reserved、CONFIRMED→sold）推导期望值，
     * 与余额计数器逐池核对。发现差异即报告，不自动修复——修复是人工或升级流程的决策。
     */
    ReconciliationReport reconcile();

    // ==== 平台座位型票品（独立模型，不复用挂牌票品 ticket_units）====

    /** 铺座（管理操作）：商品经 catalog 校验存在；同场次同物理位置重复即拒绝 */
    java.util.List<SeatView> createSeats(CreateSeatsCommand command);

    /** 座位视图；未知座位抛 SeatNotFoundException */
    SeatView seat(String seatNo);

    /** 商品的全部座位（按创建顺序） */
    java.util.List<SeatView> seatsOfProduct(String productCode);

    /** 场次的全部座位（按创建顺序）：公开座位图读路径 */
    java.util.List<SeatView> seatsOfSession(String sessionCode);

    /** 预留座位（IN_STOCK → RESERVED 并登记订单号）：并发抢座守卫裁决，败者抛 InventoryConflictException */
    SeatView reserveSeat(String seatNo, String orderNo);

    /** 确认出票（RESERVED → SOLD，订单号保留供对账） */
    SeatView confirmSeat(String seatNo);

    /** 释放座位（RESERVED → IN_STOCK 并清空订单号）：取消 / 超时链路调用 */
    SeatView releaseSeat(String seatNo);

    /** 交付（SOLD → DELIVERED，终态） */
    SeatView deliverSeat(String seatNo);

    /** 作废（IN_STOCK / RESERVED → INVALID，终态）：坏座下架 */
    SeatView invalidateSeat(String seatNo);
}
