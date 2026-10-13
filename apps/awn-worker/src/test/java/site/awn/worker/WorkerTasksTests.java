package site.awn.worker;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import site.awn.inventory.api.BalanceView;
import site.awn.inventory.api.CreatePoolCommand;
import site.awn.inventory.api.InventoryFacade;
import site.awn.inventory.api.ReconciliationReport;
import site.awn.inventory.api.ReconciliationViolation;
import site.awn.order.api.CreateOrderCommand;
import site.awn.order.api.OrderFacade;
import site.awn.order.api.OrderLineCommand;
import site.awn.order.api.OrderTimeoutResult;
import site.awn.order.api.OrderView;
import site.awn.order.domain.OrderStatus;
import site.awn.shared.outbox.OutboxMessageRepository;
import site.awn.support.AbstractPostgresIntegrationTest;
import site.awn.worker.task.InventoryReconciliationTask;
import site.awn.worker.task.OrderTimeoutTask;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Worker 任务集成测试（真实 PostgreSQL）。
 * 验收口径（todo Phase 03）：超时未支付订单释放预留；任务重复执行幂等；
 * 与支付确认竞态时不盲目释放；对账能发现计数器与预留记录的漂移。
 * 测试直接调用 Facade / 任务方法（调度开关关闭，避免定时器并发干扰断言）。
 */
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "awn.worker.enabled=false"
})
class WorkerTasksTests extends AbstractPostgresIntegrationTest {

    @Autowired OrderFacade orders;
    @Autowired InventoryFacade inventory;
    @Autowired OutboxMessageRepository outbox;
    @Autowired OrderTimeoutTask orderTimeoutTask;
    @Autowired InventoryReconciliationTask reconciliationTask;
    @Autowired JdbcTemplate jdbc;
    @Autowired site.awn.support.CatalogProductFactory products;

    private String uid() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private String newPool(int totalQuantity) {
        String poolCode = "wt-" + uid();
        inventory.createPool(new CreatePoolCommand(poolCode, products.productCode(poolCode), "sess-" + uid(), totalQuantity));
        return poolCode;
    }

    private OrderView newOrder(String poolCode, int quantity, long unitPrice) {
        return orders.createOrder(new CreateOrderCommand("wt-" + uid(), 4242L, "CNY",
                List.of(new OrderLineCommand(poolCode, "prod-" + uid(), "演出票·A档",
                        "sess-" + uid(), "A档", quantity, unitPrice, 0, 0))));
    }

    private void forceExpired(String orderNo) {
        jdbc.update("UPDATE orders SET expires_at = ? WHERE order_no = ?",
                Timestamp.from(Instant.now().minusSeconds(60)), orderNo);
    }

    private long expiredEvents(String orderNo) {
        return outbox.countByEventTypeAndAggregateId("ORDER_EXPIRED", orderNo);
    }

    /**
     * 测试库是持久的，历史遗留的过期待支付订单会混进全局批量结果。
     * 每个需要精确计数的用例先清空整批队列，再建立自己的夹具。
     */
    private OrderTimeoutResult drainExpiredQueue() {
        return orders.releaseExpiredOrders(Instant.now());
    }

    @Test
    void expiredPendingOrderIsReleasedIdempotently() {
        drainExpiredQueue();
        String pool = newPool(10);
        OrderView order = newOrder(pool, 2, 10000);

        BalanceView before = inventory.balanceOf(pool);
        assertThat(before.reservedQuantity()).isEqualTo(2);
        assertThat(before.available()).isEqualTo(8);

        forceExpired(order.orderNo());
        OrderTimeoutResult first = orders.releaseExpiredOrders(Instant.now());

        assertThat(first.expired()).isEqualTo(1);
        assertThat(first.skipped()).isZero();
        assertThat(first.failed()).isEmpty();
        assertThat(orders.orderOf(order.orderNo()).status()).isEqualTo(OrderStatus.EXPIRED);

        BalanceView after = inventory.balanceOf(pool);
        assertThat(after.reservedQuantity()).isZero();
        assertThat(after.soldQuantity()).isZero();
        assertThat(after.available()).isEqualTo(10);
        assertThat(expiredEvents(order.orderNo())).isEqualTo(1);

        // 重复执行幂等：已释放的订单不再出现在结果里，事件不重复发布
        OrderTimeoutResult second = orders.releaseExpiredOrders(Instant.now());
        assertThat(second.expired()).isZero();
        assertThat(second.skipped()).isZero();
        assertThat(second.failed()).isEmpty();
        assertThat(expiredEvents(order.orderNo())).isEqualTo(1);
    }

    @Test
    void paidOrderWithPastExpiryIsNotReleased() {
        drainExpiredQueue();
        String pool = newPool(10);
        OrderView order = newOrder(pool, 3, 5000);
        assertThat(orders.markPaid(order.orderNo())).isTrue();

        forceExpired(order.orderNo());
        OrderTimeoutResult result = orders.releaseExpiredOrders(Instant.now());

        assertThat(result.expired()).isZero();
        assertThat(result.failed()).isEmpty();
        assertThat(orders.orderOf(order.orderNo()).status()).isEqualTo(OrderStatus.PAID);
        BalanceView balance = inventory.balanceOf(pool);
        assertThat(balance.soldQuantity()).isEqualTo(3);
        assertThat(balance.reservedQuantity()).isZero();
        assertThat(expiredEvents(order.orderNo())).isZero();
    }

    @Test
    void futureExpiryIsUntouched() {
        drainExpiredQueue();
        String pool = newPool(5);
        OrderView order = newOrder(pool, 1, 100);

        OrderTimeoutResult result = orders.releaseExpiredOrders(Instant.now());

        assertThat(result.expired()).isZero();
        assertThat(result.skipped()).isZero();
        assertThat(result.failed()).isEmpty();
        assertThat(orders.orderOf(order.orderNo()).status()).isEqualTo(OrderStatus.PENDING_PAYMENT);
    }

    @Test
    void disabledTaskRunIsNoOp() {
        drainExpiredQueue();
        String pool = newPool(5);
        OrderView order = newOrder(pool, 1, 100);
        forceExpired(order.orderNo());

        // awn.worker.enabled=false：任务方法必须直接返回，不做任何迁移
        orderTimeoutTask.run();
        reconciliationTask.run();

        assertThat(orders.orderOf(order.orderNo()).status()).isEqualTo(OrderStatus.PENDING_PAYMENT);

        // 收尾正常释放，不向后续用例遗留过期待支付订单
        assertThat(drainExpiredQueue().expired()).isEqualTo(1);
        assertThat(orders.orderOf(order.orderNo()).status()).isEqualTo(OrderStatus.EXPIRED);
    }

    @Test
    void reconciliationDetectsTamperedCounter() {
        String pool = newPool(10);
        OrderView order = newOrder(pool, 2, 3000);

        ReconciliationReport baseline = inventory.reconcile();
        assertThat(baseline.violations()).noneMatch(v -> v.poolCode().equals(pool));

        try {
            jdbc.update("UPDATE inventory_balance SET reserved_quantity = reserved_quantity + 3 " +
                    "WHERE inventory_pool_id = (SELECT id FROM inventory_pool WHERE pool_code = ?)", pool);

            ReconciliationReport report = inventory.reconcile();
            List<ReconciliationViolation> ours = report.violations().stream()
                    .filter(v -> v.poolCode().equals(pool))
                    .toList();
            assertThat(ours).hasSize(1);
            assertThat(ours.get(0).kind()).isEqualTo("RESERVED_MISMATCH");
            assertThat(ours.get(0).expected()).isEqualTo(2);
            assertThat(ours.get(0).actual()).isEqualTo(5);
        } finally {
            jdbc.update("UPDATE inventory_balance SET reserved_quantity = 2 " +
                    "WHERE inventory_pool_id = (SELECT id FROM inventory_pool WHERE pool_code = ?)", pool);
        }
        assertThat(inventory.reconcile().violations()).noneMatch(v -> v.poolCode().equals(pool));
    }
}
