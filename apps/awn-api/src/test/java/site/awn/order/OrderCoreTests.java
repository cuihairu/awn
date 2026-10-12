package site.awn.order;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import site.awn.inventory.api.BalanceView;
import site.awn.inventory.api.CreatePoolCommand;
import site.awn.inventory.api.InventoryFacade;
import site.awn.inventory.domain.InsufficientInventoryException;
import site.awn.order.api.CreateOrderCommand;
import site.awn.order.api.OrderFacade;
import site.awn.order.api.OrderLineCommand;
import site.awn.order.api.OrderView;
import site.awn.order.domain.OrderConflictException;
import site.awn.order.domain.OrderStatus;
import site.awn.order.infrastructure.OrderRepository;
import site.awn.shared.outbox.OutboxMessageRepository;
import site.awn.support.AbstractPostgresIntegrationTest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 03 硬性门槛：真实 PostgreSQL 上的订单核心测试。
 * - 创建时序：幂等判重 → 逐行原子预留 → 订单与快照 → 同事务 Outbox；
 * - 幂等：同键同请求重放原结果；同键不同请求冲突；并发同键仅一单；
 * - 回滚：任一步失败整体回滚（订单、明细、Outbox、已成功的预留无一残留）；
 * - 状态机：非法迁移被拒，重复迁移幂等。
 */
@SpringBootTest
class OrderCoreTests extends AbstractPostgresIntegrationTest {

    @Autowired
    OrderFacade orderFacade;

    @Autowired
    InventoryFacade inventory;

    @Autowired
    OrderRepository orders;

    @Autowired
    OutboxMessageRepository outbox;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void createOrderReservesStockAndWritesOutbox() {
        String pool = pool("ord-core", 10);
        var line = new OrderLineCommand(pool, "prod-core", "莫文蔚演唱会 内场", "sess-core", "内场票", 2, 38000, 0, 0);
        OrderView order = orderFacade.createOrder(command("core-" + uid(), line));

        assertThat(order.orderNo()).startsWith("ORD-");
        assertThat(order.status()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(order.currency()).isEqualTo("CNY");
        assertThat(order.totalAmount()).isEqualTo(76000);
        assertThat(order.payAmount()).isEqualTo(76000);
        assertThat(order.expiresAt()).isNotNull();
        assertThat(order.items()).hasSize(1);
        assertThat(order.items().get(0).productNameSnapshot()).isEqualTo("莫文蔚演唱会 内场");
        assertThat(order.items().get(0).lineTotal()).isEqualTo(76000);
        assertThat(order.items().get(0).reservationNo()).isNotBlank();

        BalanceView balance = inventory.balanceOf(pool);
        assertThat(balance.reservedQuantity()).isEqualTo(2);
        assertThat(balance.available()).isEqualTo(8);

        assertThat(outbox.countByEventTypeAndAggregateId("ORDER_CREATED", order.orderNo())).isEqualTo(1);
    }

    @Test
    void idempotentReplayReturnsSameOrder() {
        String pool = pool("ord-replay", 10);
        var line = new OrderLineCommand(pool, "prod-replay", "演出 A", "sess-replay", null, 1, 10000, 0, 0);
        String key = "replay-" + UUID.randomUUID();

        OrderView first = orderFacade.createOrder(command(key, line));
        OrderView replay = orderFacade.createOrder(command(key, line));

        assertThat(replay.orderNo()).isEqualTo(first.orderNo());
        // 该幂等键只有一单，库存只扣一次，Outbox 只写一条
        assertThat(countOrdersByKey(key)).isEqualTo(1);
        assertThat(inventory.balanceOf(pool).reservedQuantity()).isEqualTo(1);
        assertThat(outbox.countByEventTypeAndAggregateId("ORDER_CREATED", first.orderNo())).isEqualTo(1);
    }

    @Test
    void idempotencyKeyConflictOnDifferentRequest() {
        String pool = pool("ord-conflict", 10);
        String key = "conflict-" + UUID.randomUUID();
        var lineA = new OrderLineCommand(pool, "prod-a", "演出 A", "sess-x", null, 1, 10000, 0, 0);
        var lineB = new OrderLineCommand(pool, "prod-b", "演出 B", "sess-x", null, 1, 10000, 0, 0);

        OrderView first = orderFacade.createOrder(command(key, lineA));
        assertThatThrownBy(() -> orderFacade.createOrder(command(key, lineB)))
                .isInstanceOf(OrderConflictException.class);

        // 原订单不受影响，未为第二个请求扣库存
        assertThat(orderFacade.orderOf(first.orderNo()).status()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(inventory.balanceOf(pool).reservedQuantity()).isEqualTo(1);
    }

    @Test
    void concurrentSameKeyCreatesExactlyOneOrder() throws Exception {
        String pool = pool("ord-race", 10);
        var line = new OrderLineCommand(pool, "prod-race", "演出 R", "sess-race", null, 2, 5000, 0, 0);
        String key = "race-" + UUID.randomUUID();

        int threads = 8;
        var latch = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(threads);
        List<Future<OrderView>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(() -> {
                try {
                    latch.await();
                    return orderFacade.createOrder(command(key, line));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
            }));
        }
        latch.countDown();
        String orderNo = futures.get(0).get().orderNo();
        for (Future<OrderView> f : futures) {
            assertThat(f.get().orderNo()).as("并发同键必须全部返回同一订单").isEqualTo(orderNo);
        }
        executor.shutdown();

        assertThat(countOrdersByKey(key)).isEqualTo(1);
        assertThat(inventory.balanceOf(pool).reservedQuantity()).isEqualTo(2);
        assertThat(outbox.countByEventTypeAndAggregateId("ORDER_CREATED", orderNo)).isEqualTo(1);
    }

    @Test
    void insufficientInventoryRollsBackEverything() {
        String poolA = pool("ord-rb-a", 5);
        String poolB = pool("ord-rb-b", 1);
        var lineA = new OrderLineCommand(poolA, "prod-a", "演出 A", "sess-rb", null, 1, 10000, 0, 0);
        // 行 B 库存不足：触发整体回滚
        var lineB = new OrderLineCommand(poolB, "prod-b", "演出 B", "sess-rb", null, 2, 20000, 0, 0);

        long ordersBefore = orders.count();
        long outboxBefore = outbox.count();

        assertThatThrownBy(() -> orderFacade.createOrder(command("rb-" + UUID.randomUUID(), lineA, lineB)))
                .isInstanceOf(InsufficientInventoryException.class);

        // 订单、明细、Outbox、已成功的行 A 预留：全部无残留
        assertThat(orders.count()).isEqualTo(ordersBefore);
        assertThat(outbox.count()).isEqualTo(outboxBefore);
        assertThat(inventory.balanceOf(poolA).reservedQuantity()).isZero();
        assertThat(inventory.balanceOf(poolA).available()).isEqualTo(5);
        assertThat(inventory.balanceOf(poolB).reservedQuantity()).isZero();
    }

    @Test
    void cancelReleasesStockAndIsGuarded() {
        String pool = pool("ord-cancel", 5);
        var line = new OrderLineCommand(pool, "prod-c", "演出 C", "sess-cancel", null, 3, 8000, 0, 0);
        OrderView order = orderFacade.createOrder(command("cancel-" + uid(), line));

        assertThat(orderFacade.cancel(order.orderNo())).isTrue();
        assertThat(orderFacade.orderOf(order.orderNo()).status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(inventory.balanceOf(pool).reservedQuantity()).isZero();
        assertThat(inventory.balanceOf(pool).available()).isEqualTo(5);
        assertThat(outbox.countByEventTypeAndAggregateId("ORDER_CANCELLED", order.orderNo())).isEqualTo(1);

        // 重复取消：幂等重放
        assertThat(orderFacade.cancel(order.orderNo())).isFalse();
    }

    @Test
    void markPaidConfirmsStockAndRejectsLaterCancel() {
        String pool = pool("ord-pay", 5);
        var line = new OrderLineCommand(pool, "prod-p", "演出 P", "sess-pay", null, 2, 9000, 0, 0);
        OrderView order = orderFacade.createOrder(command("pay-" + uid(), line));

        assertThat(orderFacade.markPaid(order.orderNo())).isTrue();
        BalanceView balance = inventory.balanceOf(pool);
        assertThat(balance.soldQuantity()).isEqualTo(2);
        assertThat(balance.reservedQuantity()).isZero();
        assertThat(outbox.countByEventTypeAndAggregateId("ORDER_PAID", order.orderNo())).isEqualTo(1);

        // 重复支付确认：幂等重放
        assertThat(orderFacade.markPaid(order.orderNo())).isFalse();
        // 已支付订单不可取消
        assertThatThrownBy(() -> orderFacade.cancel(order.orderNo()))
                .isInstanceOf(OrderConflictException.class);
    }

    @Test
    @org.springframework.transaction.annotation.Transactional
    void stateMachineRejectsIllegalTransitions() {
        String pool = pool("ord-fsm", 5);
        var line = new OrderLineCommand(pool, "prod-f", "演出 F", "sess-fsm", null, 1, 1000, 0, 0);
        OrderView order = orderFacade.createOrder(command("fsm-" + uid(), line));

        // 第一层：状态机定义表拒绝非法边
        assertThat(OrderStatus.PENDING_PAYMENT.canTransitionTo(OrderStatus.FULFILLED)).isFalse();
        assertThat(OrderStatus.FULFILLED.canTransitionTo(OrderStatus.FULFILLING)).isFalse();
        assertThat(OrderStatus.REFUNDED.canTransitionTo(OrderStatus.PENDING_PAYMENT)).isFalse();
        assertThat(OrderStatus.CANCELLED.isTerminal()).isTrue();
        assertThat(OrderStatus.EXPIRED.isTerminal()).isTrue();
        assertThat(OrderStatus.REFUNDED.isTerminal()).isTrue();
        // 合法边存在
        assertThat(OrderStatus.PENDING_PAYMENT.canTransitionTo(OrderStatus.PAID)).isTrue();
        assertThat(OrderStatus.PENDING_PAYMENT.canTransitionTo(OrderStatus.EXPIRED)).isTrue();
        assertThat(OrderStatus.REFUND_PENDING.canTransitionTo(OrderStatus.PAID)).isTrue();

        // 第二层：守卫迁移只在 from 匹配时生效（竞态防线）
        assertThat(orders.transition(order.orderNo(), OrderStatus.PENDING_PAYMENT, OrderStatus.PAID)).isEqualTo(1);
        // 状态已变：同一 from 的再迁移返回 0，防止重复生效
        assertThat(orders.transition(order.orderNo(), OrderStatus.PENDING_PAYMENT, OrderStatus.CANCELLED)).isZero();
        assertThat(orders.transition(order.orderNo(), OrderStatus.PENDING_PAYMENT, OrderStatus.PAID)).isZero();

        // 第三层：服务层只暴露合法边（cancel-after-paid 的冲突在 markPaidConfirmsStockAndRejectsLaterCancel 覆盖）
        assertThat(orderFacade.orderOf(order.orderNo()).status()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void orderItemsAreImmutableAtDatabaseLevel() {
        String pool = pool("ord-immutable", 5);
        var line = new OrderLineCommand(pool, "prod-i", "演出 I", "sess-immutable", null, 1, 1000, 0, 0);
        OrderView order = orderFacade.createOrder(command("immutable-" + uid(), line));

        assertThatThrownBy(() -> jdbc.execute(
                "UPDATE order_items SET quantity = 99 WHERE order_id = (SELECT id FROM orders WHERE order_no = '"
                        + order.orderNo() + "')"))
                .as("明细快照的 UPDATE 必须被数据库触发器拒绝")
                .isInstanceOf(DataAccessException.class);

        OrderView reloaded = orderFacade.orderOf(order.orderNo());
        assertThat(reloaded.items().get(0).quantity()).isEqualTo(1);
    }

    /** 按幂等键统计订单数（测试库跨用例持久，禁止用全局 count 断言） */
    private int countOrdersByKey(String key) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE idempotency_key = ?", Integer.class, key);
        return count == null ? 0 : count;
    }

    private String pool(String productPrefix, int total) {
        String poolCode = "pool-" + UUID.randomUUID().toString().substring(0, 12);
        inventory.createPool(new CreatePoolCommand(poolCode, productPrefix + "-" + UUID.randomUUID().toString().substring(0, 6),
                "sess-" + UUID.randomUUID().toString().substring(0, 6), total));
        return poolCode;
    }

    private CreateOrderCommand command(String key, OrderLineCommand... lines) {
        return new CreateOrderCommand(key, 42L, "CNY", List.of(lines));
    }

    private static String uid() {
        return UUID.randomUUID().toString().substring(0, 12);
    }
}
