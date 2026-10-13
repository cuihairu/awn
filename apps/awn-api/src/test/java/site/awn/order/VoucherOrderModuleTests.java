package site.awn.order;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import site.awn.inventory.api.CreatePoolCommand;
import site.awn.inventory.api.InventoryFacade;
import site.awn.marketplace.api.CreateListingCommand;
import site.awn.marketplace.api.CreateSellerCommand;
import site.awn.marketplace.api.ListingView;
import site.awn.marketplace.api.MarketplaceFacade;
import site.awn.marketplace.api.SubmitSupplyReviewCommand;
import site.awn.marketplace.domain.MarketplaceConflictException;
import site.awn.marketplace.domain.MarketplaceNotFoundException;
import site.awn.marketplace.domain.TicketUnitStatus;
import site.awn.order.api.CreateOrderCommand;
import site.awn.order.api.OrderFacade;
import site.awn.order.api.OrderLineCommand;
import site.awn.order.api.OrderView;
import site.awn.order.domain.OrderStatus;
import site.awn.order.infrastructure.OrderRepository;
import site.awn.shared.outbox.OutboxMessageRepository;
import site.awn.support.AbstractPostgresIntegrationTest;
import site.awn.support.CatalogProductFactory;

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
 * G2（docs/architecture/trading-model.md）：凭证型订单行接入订单链路，
 * 第三方挂牌从「事实上不可交易」变为可下单。
 *
 * <p>验收口径：下单把挂牌票品 IN_STOCK → RESERVED（与订单同事务，不触碰库存池）；
 * 取消 / 超时释放回 IN_STOCK；支付确认转 SOLD；并发抢单只有一单成交；
 * 任何一步失败整体回滚，订单不留残余。</p>
 */
@SpringBootTest
class VoucherOrderModuleTests extends AbstractPostgresIntegrationTest {

    @Autowired OrderFacade orderFacade;

    @Autowired OrderRepository orders;

    @Autowired InventoryFacade inventory;

    @Autowired MarketplaceFacade marketplace;

    @Autowired OutboxMessageRepository outbox;

    @Autowired CatalogProductFactory products;

    @Autowired JdbcTemplate jdbc;

    @Test
    void voucherOrderReservesListingUnitWithoutTouchingInventoryPool() {
        ListingView listing = listedListing();
        var line = voucherLine(listing.listingNo(), "prod-voucher", "莫文蔚演唱会 看台");

        OrderView order = orderFacade.createOrder(command("vchr-" + uid(), line));

        assertThat(order.status()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(order.items()).hasSize(1);
        // 凭证型行：挂牌引用有值，库存预留为 null
        assertThat(order.items().get(0).listingNo()).isEqualTo(listing.listingNo());
        assertThat(order.items().get(0).unitNo()).isNotBlank();
        assertThat(order.items().get(0).reservationNo()).isNull();
        assertThat(order.items().get(0).poolCode()).isNull();

        // 挂牌与票品同事务进入预留态
        assertThat(marketplace.listingOf(listing.listingNo()).status()).isEqualTo("RESERVED");
        assertThat(marketplace.unitOfListing(listing.listingNo()).status()).isEqualTo("RESERVED");

        // 凭证型行不占库存池（同商品同场次若有池，可用量不受影响）
        String pool = pool("vchr", 3);
        assertThat(inventory.balanceOf(pool).reservedQuantity()).isZero();
        assertThat(inventory.balanceOf(pool).available()).isEqualTo(3);

        assertThat(outbox.countByEventTypeAndAggregateId("ORDER_CREATED", order.orderNo())).isEqualTo(1);
    }

    @Test
    void voucherOrderRollsBackEntirelyWhenListingUnknown() {
        var line = voucherLine("LST-none", "prod-rollback", "演出 X");
        // 测试库跨用例持久：用增量断言，不用全局计数
        long ordersBefore = orders.count();
        long outboxBefore = outbox.count();

        assertThatThrownBy(() -> orderFacade.createOrder(command("vchr-rb-" + uid(), line)))
                .isInstanceOf(MarketplaceNotFoundException.class);

        // 订单、明细、Outbox 全部无残余
        assertThat(orders.count()).isEqualTo(ordersBefore);
        assertThat(outbox.count()).isEqualTo(outboxBefore);
    }

    @Test
    void cancelReleasesVoucherListingUnit() {
        ListingView listing = listedListing();
        OrderView order = orderFacade.createOrder(
                command("vchr-cancel-" + uid(), voucherLine(listing.listingNo(), "prod-c", "演出 C")));

        assertThat(orderFacade.cancel(order.orderNo())).isTrue();
        assertThat(orderFacade.orderOf(order.orderNo()).status()).isEqualTo(OrderStatus.CANCELLED);
        // 票品回到在库，挂牌回到在售，卖家可再次挂售
        assertThat(marketplace.unitOfListing(listing.listingNo()).status())
                .isEqualTo(TicketUnitStatus.IN_STOCK.name());
        assertThat(marketplace.listingOf(listing.listingNo()).status()).isEqualTo("LISTED");
        assertThat(outbox.countByEventTypeAndAggregateId("ORDER_CANCELLED", order.orderNo())).isEqualTo(1);

        // 重复取消：幂等重放
        assertThat(orderFacade.cancel(order.orderNo())).isFalse();
    }

    @Test
    void markPaidConfirmsVoucherListingUnit() {
        ListingView listing = listedListing();
        OrderView order = orderFacade.createOrder(
                command("vchr-pay-" + uid(), voucherLine(listing.listingNo(), "prod-p", "演出 P")));

        assertThat(orderFacade.markPaid(order.orderNo())).isTrue();
        assertThat(orderFacade.orderOf(order.orderNo()).status()).isEqualTo(OrderStatus.PAID);
        assertThat(marketplace.unitOfListing(listing.listingNo()).status())
                .isEqualTo(TicketUnitStatus.SOLD.name());
        assertThat(marketplace.listingOf(listing.listingNo()).status()).isEqualTo("SOLD");

        // 重复支付确认：幂等重放
        assertThat(orderFacade.markPaid(order.orderNo())).isFalse();
        // 已支付订单不可取消
        assertThatThrownBy(() -> orderFacade.cancel(order.orderNo()))
                .isInstanceOf(site.awn.order.domain.OrderConflictException.class);
    }

    @Test
    void onlyOneBuyerWinsWhenRacingForTheSameListing() throws Exception {
        ListingView listing = listedListing();
        var line = voucherLine(listing.listingNo(), "prod-race", "演出 R");
        long ordersBefore = orders.count();

        int threads = 6;
        var latch = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(threads);
        List<Future<Object>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(() -> {
                try {
                    latch.await();
                    // 各买家独立幂等键：走真实抢单路径，而非同键幂等重放
                    return orderFacade.createOrder(command("vchr-race-" + UUID.randomUUID(), line));
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
                if (e.getCause() instanceof MarketplaceConflictException) {
                    conflicts.incrementAndGet();
                } else {
                    throw e;
                }
            }
        }
        executor.shutdown();

        // 并发抢单：守卫迁移只放行一单，其余冲突回滚（票品不被重复预留）
        assertThat(wins.get()).isEqualTo(1);
        assertThat(conflicts.get()).isEqualTo(threads - 1);
        assertThat(orders.count()).isEqualTo(ordersBefore + 1);
        assertThat(marketplace.unitOfListing(listing.listingNo()).status())
                .isEqualTo(TicketUnitStatus.RESERVED.name());
        assertThat(marketplace.listingOf(listing.listingNo()).status()).isEqualTo("RESERVED");
    }

    @Test
    void timeoutReleaseAlsoFreesVoucherListingUnit() {
        ListingView listing = listedListing();
        OrderView order = orderFacade.createOrder(
                command("vchr-exp-" + uid(), voucherLine(listing.listingNo(), "prod-e", "演出 E")));

        // 直接把订单推过支付截止期，再交给超时任务释放
        jdbc.update("UPDATE orders SET expires_at = now() - interval '1 minute' WHERE order_no = ?",
                order.orderNo());

        assertThat(orderFacade.releaseExpiredOrders(java.time.Instant.now()).expired()).isGreaterThanOrEqualTo(1);
        assertThat(orderFacade.orderOf(order.orderNo()).status()).isEqualTo(OrderStatus.EXPIRED);
        assertThat(marketplace.unitOfListing(listing.listingNo()).status())
                .isEqualTo(TicketUnitStatus.IN_STOCK.name());
        assertThat(marketplace.listingOf(listing.listingNo()).status()).isEqualTo("LISTED");
    }

    @Test
    void voucherLineRequiresExactlyOneSourceAndQuantityOne() {
        // 同时给出库存池与挂牌：拒绝
        assertThatThrownBy(() -> new OrderLineCommand("pool-x", "LST-x", "prod-x", "演出 X",
                null, null, 1, 10000, 0, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactly one of poolCode or listingNo");
        // 两者都缺：拒绝
        assertThatThrownBy(() -> new OrderLineCommand(null, null, "prod-x", "演出 X",
                null, null, 1, 10000, 0, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactly one of poolCode or listingNo");
        // 凭证型行一张挂牌只能买一张票（票品 1:1）
        assertThatThrownBy(() -> new OrderLineCommand(null, "LST-x", "prod-x", "演出 X",
                null, null, 2, 10000, 0, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("voucher line quantity must be 1");
    }

    @Test
    void voucherOrderItemKeepsSingleInventoryPathAtDatabaseLevel() {
        ListingView listing = listedListing();
        OrderView order = orderFacade.createOrder(
                command("vchr-db-" + uid(), voucherLine(listing.listingNo(), "prod-db", "演出 DB")));

        // 落库形态：listing_no/unit_no 有值，reservation_no 为空
        Integer voucherPath = jdbc.queryForObject(
                "SELECT COUNT(*) FROM order_items WHERE listing_no = ? AND unit_no IS NOT NULL "
                        + "AND reservation_no IS NULL AND order_id = (SELECT id FROM orders WHERE order_no = ?)",
                Integer.class, listing.listingNo(), order.orderNo());
        assertThat(voucherPath).isEqualTo(1);

        // 数据库兜底：两条路径都空（既无库存预留又无挂牌引用）必须被 CHECK 拒绝
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO order_items (order_id, product_id, product_name_snapshot, quantity, "
                        + "unit_price, line_total) VALUES "
                        + "((SELECT id FROM orders WHERE order_no = ?), 'p-none', 'n', 1, 1, 1)",
                order.orderNo()))
                .as("一行必须恰有一条库存路径")
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void mixedOrderReservesPoolAndVoucherUnit() {
        ListingView listing = listedListing();
        String pool = pool("vchr-mix", 4);
        var poolLine = new OrderLineCommand(pool, "prod-mix", "演出 M", "sess-mix", null, 2, 5000, 0, 0);
        var voucherLine = voucherLine(listing.listingNo(), "prod-mix-v", "演出 M 看台");

        OrderView order = orderFacade.createOrder(command("vchr-mix-" + uid(), poolLine, voucherLine));

        assertThat(order.items()).hasSize(2);
        assertThat(order.items().get(0).reservationNo()).isNotBlank();
        assertThat(order.items().get(1).listingNo()).isEqualTo(listing.listingNo());

        assertThat(inventory.balanceOf(pool).reservedQuantity()).isEqualTo(2);
        assertThat(marketplace.unitOfListing(listing.listingNo()).status())
                .isEqualTo(TicketUnitStatus.RESERVED.name());
    }

    // ------------------------------------------------------------------

    private OrderLineCommand voucherLine(String listingNo, String product, String name) {
        return new OrderLineCommand(null, listingNo, product, name, null, null, 1, 6800, 0, 0);
    }

    /** 卖家 + 供货审核 + 挂牌（卖家自担，无需平台保证即可交易） */
    private ListingView listedListing() {
        String sellerNo = marketplace.registerSeller(new CreateSellerCommand(
                (long) (100 + UUID.randomUUID().toString().charAt(0) % 500),
                "卖家-" + UUID.randomUUID(), null)).sellerNo();
        var review = marketplace.submitSupplyReview(new SubmitSupplyReviewCommand(
                sellerNo, "prod-" + UUID.randomUUID().toString().substring(0, 8), null,
                2, 6800, null));
        return marketplace.createListing(new CreateListingCommand(
                sellerNo, review.reviewNo(), null, 6800, false));
    }

    private CreateOrderCommand command(String key, OrderLineCommand... lines) {
        return new CreateOrderCommand(key, 42L, "CNY", List.of(lines));
    }

    private String pool(String productPrefix, int total) {
        String poolCode = "pool-" + UUID.randomUUID().toString().substring(0, 12);
        inventory.createPool(new CreatePoolCommand(poolCode, products.productCode(productPrefix),
                "sess-" + UUID.randomUUID().toString().substring(0, 6), total));
        return poolCode;
    }

    private static String uid() {
        return UUID.randomUUID().toString().substring(0, 12);
    }
}
