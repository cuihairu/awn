package site.awn.order.api;

/**
 * 订单模块对外接口。其他模块只允许通过本接口使用订单能力，
 * 不允许直接读写订单表或绕过库存模块操作库存。
 */
public interface OrderFacade {

    /**
     * 创建订单（docs/architecture/inventory-consistency.md 时序）：
     * 幂等键加锁判重 → 逐行原子预留库存 → 写订单与明细快照 → 同事务写 Outbox。
     * 任一步失败整体回滚（含已成功的预留）。
     *
     * @return 本次创建的订单；同键同请求的重放返回原订单
     * @throws site.awn.order.domain.OrderConflictException 同键不同请求
     * @throws site.awn.inventory.domain.InsufficientInventoryException 库存不足（整体回滚）
     */
    OrderView createOrder(CreateOrderCommand command);

    OrderView orderOf(String orderNo);

    /** 运营后台订单视图：最近订单（新→旧，限 50 条） */
    java.util.List<OrderView> recentOrders();

    /** 取消待支付订单并释放全部预留。重复取消幂等返回 false；非待支付状态抛冲突。 */
    boolean cancel(String orderNo);

    /** 支付确认（Phase 04 支付模块接手调用）：转已售。重复确认幂等返回 false。 */
    boolean markPaid(String orderNo);

    /**
     * 履约失败联动（FULFILLMENT_FAILED 事件消费）：PAID / FULFILLING / FULFILLED
     * → REFUND_PENDING，进入退款异常流程。已是 REFUND_PENDING 幂等返回 false。
     */
    boolean markRefundPending(String orderNo);

    /**
     * Worker 订单超时任务：将支付截止期已过的待支付订单迁入 EXPIRED 并逐行释放预留。
     * 单订单独立事务：单个订单失败不影响其余订单；与支付确认竞态时由守卫迁移裁决，
     * 输掉的路径记录为 skipped 而不是盲目释放（docs/architecture/inventory-consistency.md）。
     */
    OrderTimeoutResult releaseExpiredOrders(java.time.Instant now);
}
