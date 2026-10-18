package site.awn.order;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import site.awn.inventory.api.CreatePoolCommand;
import site.awn.inventory.api.InventoryFacade;
import site.awn.inventory.domain.PoolNotFoundException;
import site.awn.order.api.CreateOrderCommand;
import site.awn.order.api.JoinWaitlistCommand;
import site.awn.order.api.OrderFacade;
import site.awn.order.api.OrderLineCommand;
import site.awn.order.api.WaitlistFacade;
import site.awn.order.api.WaitlistPromotionResult;
import site.awn.order.api.WaitlistView;
import site.awn.order.domain.OrderStatus;
import site.awn.order.domain.WaitlistConflictException;
import site.awn.order.domain.WaitlistNotFoundException;
import site.awn.support.AbstractPostgresIntegrationTest;
import site.awn.support.CatalogProductFactory;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 排队 / 候补模块契约（todo 购买流余留）：售罄后排队等待补票。
 *
 * <p>核心口径：候补按加入顺序 FIFO；库存释放后为队首原子预留并生成待支付订单
 * （与普通下单同源，倒计时同口径）；递补后未在支付截止期内支付则席位过期，
 * 库存回到池中顺延下一位；排队中的席位不占库存，不预生成订单。
 *
 * <p>隔离口径：测试基类不做逐用例清库，位次与递补充计都按全表扫描，
 * 因此候补表按用例前清空、用户编号按序取唯一值（见 {@link #clearWaitlistEntries()}）。
 */
@SpringBootTest
class WaitlistModuleTests extends AbstractPostgresIntegrationTest {

    @Autowired WaitlistFacade waitlist;
    @Autowired OrderFacade orders;
    @Autowired InventoryFacade inventory;
    @Autowired CatalogProductFactory products;
    @Autowired JdbcTemplate jdbc;

    private static final AtomicLong USER_SEQ = new AtomicLong(400_000L);

    @BeforeEach
    void clearWaitlistEntries() {
        jdbc.update("DELETE FROM waitlist_entries");
    }

    private String uid() {
        return UUID.randomUUID().toString().substring(0, 12);
    }

    /** 候补条目按用户维度聚合，用户编号在全量用例间取唯一值避免相互污染 */
    private long userId() {
        return USER_SEQ.getAndIncrement();
    }

    private record Pool(String poolCode, String productCode, String sessionId) {
    }

    private Pool pool(int total) {
        String suffix = uid();
        String productCode = products.productCode(suffix);
        String poolCode = "pool-" + suffix;
        inventory.createPool(new CreatePoolCommand(poolCode, productCode, "sess-" + suffix, total));
        return new Pool(poolCode, productCode, "sess-" + suffix);
    }

    private WaitlistView join(Pool pool, long userId) {
        return join(pool, userId, 2);
    }

    private WaitlistView join(Pool pool, long userId, int quantity) {
        return waitlist.join(new JoinWaitlistCommand(pool.sessionId(), pool.poolCode(),
                pool.productCode(), "候补测试票", quantity, 10000L, "CNY", userId));
    }

    @Test
    void joiningCreatesAWaitingEntryWithoutTouchingInventory() {
        Pool pool = pool(2);

        WaitlistView entry = join(pool, userId());

        assertThat(entry.waitlistNo()).startsWith("WL-");
        assertThat(entry.status()).isEqualTo("WAITING");
        assertThat(entry.position()).isEqualTo(1);
        assertThat(entry.orderNo()).isNull();
        assertThat(entry.quantity()).isEqualTo(2);
        // 排队不占库存
        assertThat(inventory.balanceOf(pool.poolCode()).available()).isEqualTo(2);
    }

    @Test
    void queuePositionFollowsJoinOrderWithinTheSamePool() {
        Pool pool = pool(2);

        WaitlistView first = join(pool, userId());
        WaitlistView second = join(pool, userId());
        WaitlistView third = join(pool, userId());

        assertThat(List.of(first.position(), second.position(), third.position()))
                .containsExactly(1, 2, 3);
    }

    @Test
    void sameUserJoiningTheSamePoolTwiceIsRejected() {
        Pool pool = pool(2);
        long user = userId();
        join(pool, user);

        assertThatThrownBy(() -> join(pool, user))
                .isInstanceOf(WaitlistConflictException.class)
                .hasMessageContaining(pool.poolCode());
    }

    @Test
    void joiningAnUnknownPoolIsRejected() {
        String suffix = uid();

        assertThatThrownBy(() -> waitlist.join(new JoinWaitlistCommand(
                "sess-" + suffix, "pool-missing-" + suffix, "prod-" + suffix, "票", 1, 1000L,
                "CNY", userId())))
                .isInstanceOf(PoolNotFoundException.class);
    }

    /** 审核整改 P0（luna-review 表一）：排队价即递补建单价，必须等于目录价 */
    @Test
    void rejectsJoinPriceDeviatingFromCatalog() {
        Pool pool = pool(2);

        assertThatThrownBy(() -> waitlist.join(new JoinWaitlistCommand(pool.sessionId(),
                pool.poolCode(), pool.productCode(), "候补测试票", 1, 9999L, "CNY", userId())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unit price mismatch");
    }

    @Test
    void nonPositiveQuantityIsRejected() {
        Pool pool = pool(2);

        assertThatThrownBy(() -> join(pool, userId(), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void promotionCreatesAPendingPaymentOrderForTheQueueHead() {
        Pool pool = pool(2);
        WaitlistView head = join(pool, userId());
        WaitlistView second = join(pool, userId());

        WaitlistPromotionResult result = waitlist.promoteAvailable(Instant.now());

        assertThat(result.promoted()).isEqualTo(1);
        assertThat(result.failed()).isEmpty();
        WaitlistView promoted = waitlist.waitlist(head.waitlistNo());
        assertThat(promoted.status()).isEqualTo("PROMOTED");
        assertThat(promoted.orderNo()).isNotNull();
        assertThat(promoted.position()).isNull();
        // 席位持有库存：2 张已为队首预留
        assertThat(inventory.balanceOf(pool.poolCode()).available()).isZero();

        var order = orders.orderOf(promoted.orderNo());
        assertThat(order.status()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(order.payAmount()).isEqualTo(20000L);
        assertThat(order.payExpiresInSeconds()).isPositive();
        assertThat(order.items()).singleElement()
                .satisfies(item -> assertThat(item.productId()).isEqualTo(pool.productCode()));

        // 队首已递补，第二位顺延为新的第 1 位
        assertThat(waitlist.waitlist(second.waitlistNo()).position()).isEqualTo(1);
    }

    @Test
    void promotionUsesCatalogPriceSnapshotAcceptedWhenJoining() {
        Pool pool = pool(2);
        WaitlistView entry = join(pool, userId(), 2);
        jdbc.update("UPDATE product SET unit_price = 12000 WHERE product_code = ?", pool.productCode());

        WaitlistPromotionResult result = waitlist.promoteAvailable(Instant.now());

        assertThat(result.promoted()).isEqualTo(1);
        assertThat(result.failed()).isEmpty();
        WaitlistView promoted = waitlist.waitlist(entry.waitlistNo());
        assertThat(promoted.status()).isEqualTo("PROMOTED");
        assertThat(orders.orderOf(promoted.orderNo()).payAmount()).isEqualTo(20000L);
    }

    @Test
    void promotionKeepsWaitingWhenInventoryIsStillShort() {
        Pool pool = pool(1);
        // 唯一的库存被正常下单占用
        orders.createOrder(new CreateOrderCommand("wl-busy-" + uid(), userId(), "CNY", List.of(
                new OrderLineCommand(pool.poolCode(), pool.productCode(), "候补测试票",
                        pool.sessionId(), null, 1, 10000L, 0, 0))));
        WaitlistView entry = join(pool, userId());

        WaitlistPromotionResult result = waitlist.promoteAvailable(Instant.now());

        assertThat(result.promoted()).isZero();
        assertThat(result.failed()).isEmpty();
        WaitlistView stillWaiting = waitlist.waitlist(entry.waitlistNo());
        assertThat(stillWaiting.status()).isEqualTo("WAITING");
        assertThat(stillWaiting.position()).isEqualTo(1);
        assertThat(stillWaiting.orderNo()).isNull();
    }

    @Test
    void promotionIsIdempotentAcrossRuns() {
        Pool pool = pool(2);
        join(pool, userId());

        waitlist.promoteAvailable(Instant.now());
        WaitlistPromotionResult second = waitlist.promoteAvailable(Instant.now());

        assertThat(second.promoted()).isZero();
        assertThat(second.failed()).isEmpty();
    }

    @Test
    void promotedSeatBecomesClaimedOnceTheOrderIsPaid() {
        Pool pool = pool(2);
        WaitlistView head = join(pool, userId());
        waitlist.promoteAvailable(Instant.now());
        String orderNo = waitlist.waitlist(head.waitlistNo()).orderNo();
        orders.markPaid(orderNo);

        WaitlistPromotionResult result = waitlist.promoteAvailable(Instant.now());

        assertThat(result.claimed()).isEqualTo(1);
        assertThat(result.failed()).isEmpty();
        assertThat(waitlist.waitlist(head.waitlistNo()).status()).isEqualTo("CLAIMED");
    }

    @Test
    void promotedSeatExpiresWhenTheOrderTimesOut() {
        Pool pool = pool(2);
        WaitlistView head = join(pool, userId());
        WaitlistView second = join(pool, userId());
        waitlist.promoteAvailable(Instant.now());
        orders.releaseExpiredOrders(Instant.now().plusSeconds(3600));

        WaitlistPromotionResult result = waitlist.promoteAvailable(Instant.now());

        assertThat(result.expired()).isEqualTo(1);
        assertThat(result.failed()).isEmpty();
        assertThat(waitlist.waitlist(head.waitlistNo()).status()).isEqualTo("EXPIRED");
        // 过期席位释放的库存在本轮顺延给下一位
        assertThat(result.promoted()).isEqualTo(1);
        assertThat(waitlist.waitlist(second.waitlistNo()).status()).isEqualTo("PROMOTED");
    }

    @Test
    void promotedSeatExpiresWhenTheOrderIsCancelled() {
        Pool pool = pool(2);
        WaitlistView head = join(pool, userId());
        waitlist.promoteAvailable(Instant.now());
        orders.cancel(waitlist.waitlist(head.waitlistNo()).orderNo());

        waitlist.promoteAvailable(Instant.now());

        assertThat(waitlist.waitlist(head.waitlistNo()).status()).isEqualTo("EXPIRED");
    }

    @Test
    void cancelIsIdempotentAndRejectedAfterPromotion() {
        Pool pool = pool(2);
        WaitlistView entry = join(pool, userId());

        assertThat(waitlist.cancel(entry.waitlistNo())).isTrue();
        assertThat(waitlist.cancel(entry.waitlistNo())).isFalse();
        assertThat(waitlist.waitlist(entry.waitlistNo()).status()).isEqualTo("CANCELLED");

        Pool other = pool(2);
        WaitlistView promoted = join(other, userId());
        waitlist.promoteAvailable(Instant.now());
        assertThat(waitlist.waitlist(promoted.waitlistNo()).status()).isEqualTo("PROMOTED");

        assertThatThrownBy(() -> waitlist.cancel(promoted.waitlistNo()))
                .isInstanceOf(WaitlistConflictException.class);
    }

    @Test
    void waitlistOfUserListsEntriesNewestFirst() {
        long user = userId();
        WaitlistView first = join(pool(2), user);
        WaitlistView second = join(pool(2), user, 1);

        List<WaitlistView> mine = waitlist.waitlistOf(user);

        assertThat(mine).extracting(WaitlistView::waitlistNo)
                .containsExactly(second.waitlistNo(), first.waitlistNo());
    }

    @Test
    void unknownWaitlistEntryIsRejected() {
        assertThatThrownBy(() -> waitlist.waitlist("WL-missing"))
                .isInstanceOf(WaitlistNotFoundException.class);
        assertThatThrownBy(() -> waitlist.cancel("WL-missing"))
                .isInstanceOf(WaitlistNotFoundException.class);
    }
}
