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
import site.awn.order.api.AttendeeCommand;
import site.awn.order.api.CreateOrderCommand;
import site.awn.order.api.OrderFacade;
import site.awn.order.api.OrderLineCommand;
import site.awn.order.api.OrderView;
import site.awn.order.domain.IdempotencyConflictException;
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
    site.awn.support.CatalogProductFactory products;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void createOrderReservesStockAndWritesOutbox() {
        PoolRef ref = pool("ord-core", 10);
        var line = new OrderLineCommand(ref.poolCode(), ref.productCode(), "莫文蔚演唱会 内场", "sess-core", "内场票", 2, 10000, 0, 0);
        OrderView order = orderFacade.createOrder(command("core-" + uid(), line));

        assertThat(order.orderNo()).startsWith("ORD-");
        assertThat(order.status()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(order.currency()).isEqualTo("CNY");
        assertThat(order.totalAmount()).isEqualTo(20000);
        assertThat(order.payAmount()).isEqualTo(20000);
        assertThat(order.expiresAt()).isNotNull();
        assertThat(order.items()).hasSize(1);
        assertThat(order.items().get(0).productNameSnapshot()).isEqualTo("莫文蔚演唱会 内场");
        assertThat(order.items().get(0).lineTotal()).isEqualTo(20000);
        assertThat(order.items().get(0).reservationNo()).isNotBlank();

        BalanceView balance = inventory.balanceOf(ref.poolCode());
        assertThat(balance.reservedQuantity()).isEqualTo(2);
        assertThat(balance.available()).isEqualTo(8);

        assertThat(outbox.countByEventTypeAndAggregateId("ORDER_CREATED", order.orderNo())).isEqualTo(1);
    }

    /** 审核整改 P0（luna-review 表一）：金额由服务端按目录权威核价，请求方改价即拒 */
    @Test
    void rejectsUnitPriceTamperingAgainstCatalogPrice() {
        String productCode = products.productCode("ord-tamper");
        String poolCode = "pool-" + uid();
        inventory.createPool(new CreatePoolCommand(poolCode, productCode, "sess-" + uid(), 10));

        var line = new OrderLineCommand(poolCode, productCode, "演出 T", "sess-tamper", null, 1, 1, 0, 0);
        assertThatThrownBy(() -> orderFacade.createOrder(command("tamper-" + uid(), line)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unit price mismatch");
        // 核价失败时不得占用库存（校验先于任何预留）
        assertThat(inventory.balanceOf(poolCode).reservedQuantity()).isZero();
    }

    /** 审核整改 P0：服务端暂无优惠/费用规则，请求自带 discount / fee 一律拒绝 */
    @Test
    void rejectsClientSuppliedDiscountOrFee() {
        String productCode = products.productCode("ord-discount");
        String poolCode = "pool-" + uid();
        inventory.createPool(new CreatePoolCommand(poolCode, productCode, "sess-" + uid(), 10));

        var line = new OrderLineCommand(poolCode, productCode, "演出 D", "sess-discount", null, 1, 10000, 500, 0);
        assertThatThrownBy(() -> orderFacade.createOrder(command("discount-" + uid(), line)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("discount/fee");
    }

    /** 审核整改 P0：池 ↔ 商品一致性（行商品写错不得用错池的库存与价格） */
    @Test
    void rejectsPoolOfAnotherProduct() {
        String productA = products.productCode("ord-pa");
        String productB = products.productCode("ord-pb");
        String poolOfA = "pool-" + uid();
        inventory.createPool(new CreatePoolCommand(poolOfA, productA, "sess-" + uid(), 10));
        String poolOfB = "pool-" + uid();
        inventory.createPool(new CreatePoolCommand(poolOfB, productB, "sess-" + uid(), 10));

        // A 商品的编码 + B 商品的池（两商品同价 10000）：价格一致也必须拒绝
        var line = new OrderLineCommand(poolOfB, productA, "演出 X", "sess-poolmix", null, 1, 10000, 0, 0);
        assertThatThrownBy(() -> orderFacade.createOrder(command("poolmix-" + uid(), line)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not belong to product");
        assertThat(inventory.balanceOf(poolOfB).reservedQuantity()).isZero();
    }

    @Test
    void idempotentReplayReturnsSameOrder() {
        PoolRef ref = pool("ord-replay", 10);
        var line = new OrderLineCommand(ref.poolCode(), ref.productCode(), "演出 A", "sess-replay", null, 1, 10000, 0, 0);
        String key = "replay-" + UUID.randomUUID();

        OrderView first = orderFacade.createOrder(command(key, line));
        OrderView replay = orderFacade.createOrder(command(key, line));

        assertThat(replay.orderNo()).isEqualTo(first.orderNo());
        // 该幂等键只有一单，库存只扣一次，Outbox 只写一条
        assertThat(countOrdersByKey(key)).isEqualTo(1);
        assertThat(inventory.balanceOf(ref.poolCode()).reservedQuantity()).isEqualTo(1);
        assertThat(outbox.countByEventTypeAndAggregateId("ORDER_CREATED", first.orderNo())).isEqualTo(1);
    }

    @Test
    void idempotencyKeyConflictOnDifferentRequest() {
        PoolRef refA = pool("ord-conflict-a", 10);
        PoolRef refB = pool("ord-conflict-b", 10);
        String key = "conflict-" + UUID.randomUUID();
        var lineA = new OrderLineCommand(refA.poolCode(), refA.productCode(), "演出 A", "sess-x", null, 1, 10000, 0, 0);
        var lineB = new OrderLineCommand(refB.poolCode(), refB.productCode(), "演出 B", "sess-x", null, 1, 10000, 0, 0);

        OrderView first = orderFacade.createOrder(command(key, lineA));
        assertThatThrownBy(() -> orderFacade.createOrder(command(key, lineB)))
                .isInstanceOf(OrderConflictException.class);

        // 原订单不受影响，未为第二个请求扣库存
        assertThat(orderFacade.orderOf(first.orderNo()).status()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(inventory.balanceOf(refA.poolCode()).reservedQuantity()).isEqualTo(1);
    }

    @Test
    void concurrentSameKeyCreatesExactlyOneOrder() throws Exception {
        PoolRef ref = pool("ord-race", 10);
        var line = new OrderLineCommand(ref.poolCode(), ref.productCode(), "演出 R", "sess-race", null, 2, 10000, 0, 0);
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
        assertThat(inventory.balanceOf(ref.poolCode()).reservedQuantity()).isEqualTo(2);
        assertThat(outbox.countByEventTypeAndAggregateId("ORDER_CREATED", orderNo)).isEqualTo(1);
    }

    @Test
    void insufficientInventoryRollsBackEverything() {
        PoolRef refA = pool("ord-rb-a", 5);
        PoolRef refB = pool("ord-rb-b", 1);
        var lineA = new OrderLineCommand(refA.poolCode(), refA.productCode(), "演出 A", "sess-rb", null, 1, 10000, 0, 0);
        // 行 B 库存不足：触发整体回滚
        var lineB = new OrderLineCommand(refB.poolCode(), refB.productCode(), "演出 B", "sess-rb", null, 2, 10000, 0, 0);

        long ordersBefore = orders.count();
        long outboxBefore = outbox.count();

        assertThatThrownBy(() -> orderFacade.createOrder(command("rb-" + UUID.randomUUID(), lineA, lineB)))
                .isInstanceOf(InsufficientInventoryException.class);

        // 订单、明细、Outbox、已成功的行 A 预留：全部无残留
        assertThat(orders.count()).isEqualTo(ordersBefore);
        assertThat(outbox.count()).isEqualTo(outboxBefore);
        assertThat(inventory.balanceOf(refA.poolCode()).reservedQuantity()).isZero();
        assertThat(inventory.balanceOf(refA.poolCode()).available()).isEqualTo(5);
        assertThat(inventory.balanceOf(refB.poolCode()).reservedQuantity()).isZero();
    }

    @Test
    void cancelReleasesStockAndIsGuarded() {
        PoolRef ref = pool("ord-cancel", 5);
        var line = new OrderLineCommand(ref.poolCode(), ref.productCode(), "演出 C", "sess-cancel", null, 3, 10000, 0, 0);
        OrderView order = orderFacade.createOrder(command("cancel-" + uid(), line));

        assertThat(orderFacade.cancel(order.orderNo())).isTrue();
        assertThat(orderFacade.orderOf(order.orderNo()).status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(inventory.balanceOf(ref.poolCode()).reservedQuantity()).isZero();
        assertThat(inventory.balanceOf(ref.poolCode()).available()).isEqualTo(5);
        assertThat(outbox.countByEventTypeAndAggregateId("ORDER_CANCELLED", order.orderNo())).isEqualTo(1);

        // 重复取消：幂等重放
        assertThat(orderFacade.cancel(order.orderNo())).isFalse();
    }

    @Test
    void markPaidConfirmsStockAndRejectsLaterCancel() {
        PoolRef ref = pool("ord-pay", 5);
        var line = new OrderLineCommand(ref.poolCode(), ref.productCode(), "演出 P", "sess-pay", null, 2, 10000, 0, 0);
        OrderView order = orderFacade.createOrder(command("pay-" + uid(), line));

        assertThat(orderFacade.markPaid(order.orderNo())).isTrue();
        BalanceView balance = inventory.balanceOf(ref.poolCode());
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
        PoolRef ref = pool("ord-fsm", 5);
        var line = new OrderLineCommand(ref.poolCode(), ref.productCode(), "演出 F", "sess-fsm", null, 1, 10000, 0, 0);
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
        PoolRef ref = pool("ord-immutable", 5);
        var line = new OrderLineCommand(ref.poolCode(), ref.productCode(), "演出 I", "sess-immutable", null, 1, 10000, 0, 0);
        OrderView order = orderFacade.createOrder(command("immutable-" + uid(), line));

        assertThatThrownBy(() -> jdbc.execute(
                "UPDATE order_items SET quantity = 99 WHERE order_id = (SELECT id FROM orders WHERE order_no = '"
                        + order.orderNo() + "')"))
                .as("明细快照的 UPDATE 必须被数据库触发器拒绝")
                .isInstanceOf(DataAccessException.class);

        OrderView reloaded = orderFacade.orderOf(order.orderNo());
        assertThat(reloaded.items().get(0).quantity()).isEqualTo(1);
    }

    @Test
    void attendeesBindOneTicketOneIdAndMaskOnRead() {
        PoolRef ref = pool("ord-att", 10);
        String sessionId = "sess-att-" + uid();
        var line = new OrderLineCommand(ref.poolCode(), ref.productCode(), "演出 实名", sessionId, "内场票", 2, 10000, 0, 0,
                List.of(new AttendeeCommand("张三", "11010119900307861X"),
                        new AttendeeCommand("李四", "310101199502140029")));
        OrderView order = orderFacade.createOrder(command("att-view-" + uid(), line));

        var attendees = order.items().get(0).attendees();
        assertThat(attendees).hasSize(2);
        assertThat(attendees.get(0).name()).isEqualTo("张三");
        // 证件号展示脱敏：留前 3 后 4，其余打码
        assertThat(attendees.get(0).idNumber()).isEqualTo("110***********861X");
        assertThat(attendees.get(1).idNumber()).isEqualTo("310***********0029");
        // 原文落库供实名校验，读面才脱敏（按本单统计：测试库跨用例持久，禁用全局 count）
        Integer raw = jdbc.queryForObject(
                "SELECT COUNT(*) FROM order_attendees a JOIN order_items i ON a.item_id = i.id "
                        + "JOIN orders o ON i.order_id = o.id "
                        + "WHERE o.order_no = ? AND a.id_no = '11010119900307861X'",
                Integer.class, order.orderNo());
        assertThat(raw).isEqualTo(1);
    }

    @Test
    void attendeeCountMustMatchQuantity() {
        // 一票一证：attendees 数量必须等于行数量（命令构造期守卫，HTTP 层同路径 400）
        assertThatThrownBy(() -> new OrderLineCommand(
                "pool-att-count", "prod-att-c", "演出 C", "sess-att-c", null, 2, 1000, 0, 0,
                List.of(new AttendeeCommand("张三", "11010119900307861X"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("attendee count");
    }

    @Test
    void attendeeFieldsRequired() {
        assertThatThrownBy(() -> new AttendeeCommand("  ", "11010119900307861X"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("attendee name");

        assertThatThrownBy(() -> new AttendeeCommand("张三", " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("attendee idNumber");
    }

    @Test
    void sameIdRejectedTwiceInOneOrderSameSession() {
        PoolRef ref = pool("ord-att-dup1", 5);
        String idNo = "11010119900307861X";
        var line = new OrderLineCommand(ref.poolCode(), ref.productCode(), "演出 D1", "sess-att-dup-" + uid(), null, 2, 10000, 0, 0,
                List.of(new AttendeeCommand("张三", idNo), new AttendeeCommand("张三", idNo)));
        assertThatThrownBy(() -> orderFacade.createOrder(command("att-d1-" + uid(), line)))
                .isInstanceOf(OrderConflictException.class);
    }

    @Test
    void sameIdRejectedAcrossOrdersInSameSession() {
        String idNo = "11010119900307861X";
        String sessionId = "sess-att-x-" + uid();
        PoolRef refA = pool("ord-att-x1", 5);
        PoolRef refB = pool("ord-att-x2", 5);
        orderFacade.createOrder(command("att-x1-" + uid(), new OrderLineCommand(
                refA.poolCode(), refA.productCode(), "演出 X1", sessionId, null, 1, 10000, 0, 0,
                List.of(new AttendeeCommand("张三", idNo)))));

        assertThatThrownBy(() -> orderFacade.createOrder(command("att-x2-" + uid(), new OrderLineCommand(
                refB.poolCode(), refB.productCode(), "演出 X2", sessionId, null, 1, 10000, 0, 0,
                List.of(new AttendeeCommand("李四", idNo))))))
                .isInstanceOf(OrderConflictException.class);

        // 同证件不同场次不受限
        OrderView other = orderFacade.createOrder(command("att-x3-" + uid(), new OrderLineCommand(
                refB.poolCode(), refB.productCode(), "演出 X3", "sess-att-other-" + uid(), null, 1, 10000, 0, 0,
                List.of(new AttendeeCommand("李四", idNo)))));
        assertThat(other.status()).isEqualTo(OrderStatus.PENDING_PAYMENT);
    }

    @Test
    void ordersWithoutAttendeesStayBackwardCompatible() {
        PoolRef ref = pool("ord-att-legacy", 5);
        var line = new OrderLineCommand(ref.poolCode(), ref.productCode(), "演出 L", "sess-att-l", null, 1, 10000, 0, 0);
        OrderView order = orderFacade.createOrder(command("att-l-" + uid(), line));
        assertThat(order.items().get(0).attendees()).isEmpty();
    }

    @Test
    void attendeeChangeIsIdempotencyConflict() {
        PoolRef ref = pool("ord-att-key", 5);
        String key = "att-key-" + UUID.randomUUID();
        String sessionId = "sess-att-k-" + uid();
        var withA = new OrderLineCommand(ref.poolCode(), ref.productCode(), "演出 K", sessionId, null, 1, 10000, 0, 0,
                List.of(new AttendeeCommand("张三", "11010119900307861X")));
        var withB = new OrderLineCommand(ref.poolCode(), ref.productCode(), "演出 K", sessionId, null, 1, 10000, 0, 0,
                List.of(new AttendeeCommand("李四", "11010119900307861X")));

        orderFacade.createOrder(command(key, withA));
        // 仅实名不同、行完全一致：同键不同请求仍判冲突（指纹含 attendees）
        assertThatThrownBy(() -> orderFacade.createOrder(command(key, withB)))
                .isInstanceOf(IdempotencyConflictException.class);
    }

    /** 按幂等键统计订单数（测试库跨用例持久，禁止用全局 count 断言） */
    private int countOrdersByKey(String key) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE idempotency_key = ?", Integer.class, key);
        return count == null ? 0 : count;
    }

    /** 池 + 其真实商品（核价契约后行 productId 必须用池的商品编码） */
    private record PoolRef(String poolCode, String productCode) {
    }

    private PoolRef pool(String productPrefix, int total) {
        String productCode = products.productCode(productPrefix);
        String poolCode = "pool-" + UUID.randomUUID().toString().substring(0, 12);
        inventory.createPool(new CreatePoolCommand(poolCode, productCode,
                "sess-" + UUID.randomUUID().toString().substring(0, 6), total));
        return new PoolRef(poolCode, productCode);
    }

    private CreateOrderCommand command(String key, OrderLineCommand... lines) {
        return new CreateOrderCommand(key, 42L, "CNY", List.of(lines));
    }

    private static String uid() {
        return UUID.randomUUID().toString().substring(0, 12);
    }
}
