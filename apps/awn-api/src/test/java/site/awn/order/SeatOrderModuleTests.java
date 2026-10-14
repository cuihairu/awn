package site.awn.order;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import site.awn.inventory.api.CreateSeatsCommand;
import site.awn.inventory.api.InventoryFacade;
import site.awn.inventory.api.SeatView;
import site.awn.inventory.domain.InventoryConflictException;
import site.awn.order.api.CreateOrderCommand;
import site.awn.order.api.OrderFacade;
import site.awn.order.api.OrderLineCommand;
import site.awn.order.api.OrderView;
import site.awn.order.domain.OrderStatus;
import site.awn.support.CatalogProductFactory;
import site.awn.support.AbstractPostgresIntegrationTest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 选座下单契约（todo 2D 座位图余项）：订单行座位引用（seat_no 第三条库存路径）。
 * 下单 = 逐座 IN_STOCK → RESERVED（守卫迁移同事务，并发抢座只放行一单）；
 * 取消 / 超时释放回 IN_STOCK；支付确认转 SOLD；商品一致性（座位商品 ≠ 行商品拒绝）；
 * 三路径互斥（数量型 poolCode / 凭证型 listingNo / 座位型 seatNo）。
 */
@SpringBootTest
class SeatOrderModuleTests extends AbstractPostgresIntegrationTest {

    @Autowired OrderFacade orderFacade;
    @Autowired InventoryFacade inventory;
    @Autowired CatalogProductFactory products;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Test
    void seatOrderReservesSeatInSameTransaction() {
        var seat = seatOfStock("prod-seat-a", 6800);

        OrderView order = orderFacade.createOrder(command("seat-" + uid(),
                seatLine(seat.seatNo(), seat.productCode(), 6800)));

        assertThat(order.status()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(order.items()).hasSize(1);
        // 座位型行：座位引用有值，池预留与挂牌引用都为空
        assertThat(order.items().get(0).seatNo()).isEqualTo(seat.seatNo());
        assertThat(order.items().get(0).reservationNo()).isNull();
        assertThat(order.items().get(0).listingNo()).isNull();
        assertThat(order.items().get(0).unitNo()).isNull();

        SeatView reserved = inventory.seat(seat.seatNo());
        assertThat(reserved.status().name()).isEqualTo("RESERVED");
        assertThat(reserved.orderNo()).isEqualTo(order.orderNo());
    }

    @Test
    void seatLineRequiresExactlyOneSourceAndQuantityOne() {
        // canonical 构造参数序：poolCode, listingNo, seatNo, productId, 名称, sessionId, 票档, 数量, 单价, 优惠, 费用
        // 库存池 + 挂牌同时给出：拒绝
        assertThatThrownBy(() -> new OrderLineCommand("pool-x", "LST-x", null, "prod-x", "演出 X",
                null, null, 1, 10000, 0, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactly one of poolCode, listingNo or seatNo");
        // 座位 + 挂牌同时给出：拒绝
        assertThatThrownBy(() -> new OrderLineCommand(null, "LST-x", "SEAT-x", "prod-x", "演出 X",
                null, null, 1, 10000, 0, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactly one of poolCode, listingNo or seatNo");
        // 座位 + 库存池同时给出：拒绝
        assertThatThrownBy(() -> new OrderLineCommand("pool-x", null, "SEAT-x", "prod-x", "演出 X",
                null, null, 1, 10000, 0, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactly one of poolCode, listingNo or seatNo");
        // 三者都缺：拒绝
        assertThatThrownBy(() -> new OrderLineCommand(null, null, null, "prod-x", "演出 X",
                null, null, 1, 10000, 0, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactly one of poolCode, listingNo or seatNo");
        // 座位型一行一座（座位 1:1）
        assertThatThrownBy(() -> new OrderLineCommand(null, null, "SEAT-x", "prod-x", "演出 X",
                null, null, 2, 10000, 0, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("seat line quantity must be 1");
    }

    @Test
    void seatMustBelongToTheOrderedProduct() {
        var seatA = seatOfStock("prod-seat-b", 6800);
        long ordersBefore = jdbc.queryForObject("SELECT COUNT(*) FROM orders", Long.class);

        // 用商品 B 的行去占商品 A 的座位：拒绝且不留残余
        assertThatThrownBy(() -> orderFacade.createOrder(command("seat-mix-" + uid(),
                seatLine(seatA.seatNo(), "prod-other", 6800))))
                .isInstanceOf(InventoryConflictException.class)
                .hasMessageContaining("belongs to product");

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders", Long.class)).isEqualTo(ordersBefore);
        assertThat(inventory.seat(seatA.seatNo()).status().name()).isEqualTo("IN_STOCK");
    }

    @Test
    void unknownSeatLeavesNoResidue() {
        long ordersBefore = jdbc.queryForObject("SELECT COUNT(*) FROM orders", Long.class);

        assertThatThrownBy(() -> orderFacade.createOrder(command("seat-none-" + uid(),
                seatLine("SEAT-none", "prod-seat-c", 6800))))
                .isInstanceOf(site.awn.inventory.domain.SeatNotFoundException.class);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders", Long.class)).isEqualTo(ordersBefore);
    }

    @Test
    void onlyOneBuyerWinsWhenRacingForTheSameSeat() throws Exception {
        var seat = seatOfStock("prod-seat-d", 6800);
        var line = seatLine(seat.seatNo(), seat.productCode(), 6800);
        long ordersBefore = jdbc.queryForObject("SELECT COUNT(*) FROM orders", Long.class);

        int threads = 6;
        var latch = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(threads);
        List<Future<Object>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(() -> {
                try {
                    latch.await();
                    return orderFacade.createOrder(command("seat-race-" + UUID.randomUUID(), line));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
            }));
        }
        latch.countDown();

        var wins = new AtomicInteger();
        var conflicts = new AtomicInteger();
        for (Future<Object> f : futures) {
            try {
                f.get();
                wins.incrementAndGet();
            } catch (java.util.concurrent.ExecutionException e) {
                if (e.getCause() instanceof InventoryConflictException) {
                    conflicts.incrementAndGet();
                } else {
                    throw e;
                }
            }
        }
        executor.shutdown();

        // 并发抢座：守卫迁移只放行一单，其余冲突回滚（座位不被重复预留）
        assertThat(wins.get()).isEqualTo(1);
        assertThat(conflicts.get()).isEqualTo(threads - 1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders", Long.class))
                .isEqualTo(ordersBefore + 1);
        assertThat(inventory.seat(seat.seatNo()).status().name()).isEqualTo("RESERVED");
    }

    @Test
    void cancelReleasesSeatBackToInStock() {
        var seat = seatOfStock("prod-seat-e", 6800);
        OrderView order = orderFacade.createOrder(command("seat-cancel-" + uid(),
                seatLine(seat.seatNo(), seat.productCode(), 6800)));

        assertThat(orderFacade.cancel(order.orderNo())).isTrue();
        SeatView released = inventory.seat(seat.seatNo());
        assertThat(released.status().name()).isEqualTo("IN_STOCK");
        assertThat(released.orderNo()).isNull();

        // 释放后的座位可再次被下单
        OrderView again = orderFacade.createOrder(command("seat-again-" + uid(),
                seatLine(seat.seatNo(), seat.productCode(), 6800)));
        assertThat(inventory.seat(seat.seatNo()).orderNo()).isEqualTo(again.orderNo());
    }

    @Test
    void paymentConfirmMarksSeatSold() {
        var seat = seatOfStock("prod-seat-f", 6800);
        OrderView order = orderFacade.createOrder(command("seat-pay-" + uid(),
                seatLine(seat.seatNo(), seat.productCode(), 6800)));

        assertThat(orderFacade.markPaid(order.orderNo())).isTrue();
        SeatView sold = inventory.seat(seat.seatNo());
        assertThat(sold.status().name()).isEqualTo("SOLD");
        // 售出后订单号保持，供对账与交付追溯
        assertThat(sold.orderNo()).isEqualTo(order.orderNo());

        // 已支付订单不可取消
        assertThatThrownBy(() -> orderFacade.cancel(order.orderNo()))
                .isInstanceOf(site.awn.order.domain.OrderConflictException.class);
    }

    @Test
    void timeoutReleaseAlsoFreesSeat() {
        var seat = seatOfStock("prod-seat-g", 6800);
        OrderView order = orderFacade.createOrder(command("seat-exp-" + uid(),
                seatLine(seat.seatNo(), seat.productCode(), 6800)));

        jdbc.update("UPDATE orders SET expires_at = now() - interval '1 minute' WHERE order_no = ?",
                order.orderNo());

        assertThat(orderFacade.releaseExpiredOrders(java.time.Instant.now()).expired())
                .isGreaterThanOrEqualTo(1);
        assertThat(orderFacade.orderOf(order.orderNo()).status()).isEqualTo(OrderStatus.EXPIRED);
        SeatView released = inventory.seat(seat.seatNo());
        assertThat(released.status().name()).isEqualTo("IN_STOCK");
        assertThat(released.orderNo()).isNull();
    }

    // ------------------------------------------------------------------

    /** 铺一个可售座位（商品经 catalog 校验，场次用随机字符串编码） */
    private SeatView seatOfStock(String productPrefix, long price) {
        String productCode = products.productCode(productPrefix);
        String sessionCode = "sess-seat-" + UUID.randomUUID().toString().substring(0, 8);
        List<SeatView> seats = inventory.createSeats(new CreateSeatsCommand(
                productCode, sessionCode,
                List.of(new CreateSeatsCommand.SeatPosition("A区", "1", String.valueOf(price % 10)))));
        return seats.get(0);
    }

    private OrderLineCommand seatLine(String seatNo, String product, long unitPrice) {
        return OrderLineCommand.seatLine(seatNo, product, "座位票", "sess-x", null, unitPrice, 0, 0);
    }

    private CreateOrderCommand command(String key, OrderLineCommand... lines) {
        return new CreateOrderCommand(key, 42L, "CNY", List.of(lines));
    }

    private static String uid() {
        return UUID.randomUUID().toString().substring(0, 12);
    }
}
