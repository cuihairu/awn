package site.awn.inventory.api;

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
}
