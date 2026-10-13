package site.awn.inventory;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import site.awn.inventory.api.CreatePoolCommand;
import site.awn.inventory.api.InventoryFacade;
import site.awn.inventory.api.ReservationExpiryResult;
import site.awn.inventory.api.ReserveInventoryCommand;
import site.awn.support.AbstractPostgresIntegrationTest;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 库存预留到期扫描：带 TTL 的预留过期后必须被扫描释放，库存回补；
 * 未过期预留不受影响；重复扫描幂等（不重复回补）。
 *
 * 扫描是全局的（按 expiresAt 取批次），共享测试库留存历史过期预留，
 * 因此断言只核对本用例自己的池，不用全局 released 计数。
 */
@SpringBootTest
class InventoryExpiryTests extends AbstractPostgresIntegrationTest {

    private static final int TOTAL = 10;
    private static final int QTY = 2;

    @Autowired
    InventoryFacade inventory;

    @Test
    void expiredReservationIsReleasedAndInventoryRestored() {
        String poolCode = unique("expiry");
        reserve(poolCode);
        assertThat(inventory.balanceOf(poolCode).available()).as("预留后可用量").isEqualTo(TOTAL - QTY);

        // 以未来时间作为扫描基准：本用例预留已过期
        inventory.releaseExpiredReservations(Instant.now().plusSeconds(3600));

        assertThat(inventory.balanceOf(poolCode).available()).as("过期释放后库存回补").isEqualTo(TOTAL);
    }

    @Test
    void nonExpiredReservationIsNotReleased() {
        String poolCode = unique("fresh");
        reserve(poolCode);
        assertThat(inventory.balanceOf(poolCode).available()).as("预留后可用量").isEqualTo(TOTAL - QTY);

        // 以当前时间作为扫描基准：本用例预留（1 小时 TTL）未过期
        inventory.releaseExpiredReservations(Instant.now());

        assertThat(inventory.balanceOf(poolCode).available()).as("未过期预留不得释放").isEqualTo(TOTAL - QTY);
    }

    @Test
    void expiryScanIsIdempotent() {
        String poolCode = unique("idem");
        reserve(poolCode);
        assertThat(inventory.balanceOf(poolCode).available()).as("预留后可用量").isEqualTo(TOTAL - QTY);

        Instant future = Instant.now().plusSeconds(3600);
        inventory.releaseExpiredReservations(future);
        assertThat(inventory.balanceOf(poolCode).available()).as("首轮释放后回补").isEqualTo(TOTAL);

        // 第二轮：本预留已 RELEASED，不得重复回补
        ReservationExpiryResult second = inventory.releaseExpiredReservations(future);
        assertThat(second.failed()).as("幂等重放无异常").isEmpty();
        assertThat(inventory.balanceOf(poolCode).available()).as("重复扫描不改变库存").isEqualTo(TOTAL);
    }

    private void reserve(String poolCode) {
        inventory.createPool(new CreatePoolCommand(poolCode, "prod-" + poolCode, "sess-" + poolCode, TOTAL));
        inventory.reserve(new ReserveInventoryCommand(poolCode, "ORD-" + poolCode, QTY, Duration.ofSeconds(30)));
    }

    private static String unique(String prefix) {
        return prefix + "-" + java.util.UUID.randomUUID().toString().substring(0, 8);
    }
}
