package site.awn.inventory;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import site.awn.inventory.api.BalanceView;
import site.awn.inventory.api.InventoryFacade;
import site.awn.inventory.api.PoolView;
import site.awn.inventory.api.ReservationView;
import site.awn.inventory.api.ReserveInventoryCommand;
import site.awn.inventory.domain.InsufficientInventoryException;
import site.awn.inventory.domain.InventoryConflictException;
import site.awn.support.AbstractPostgresIntegrationTest;

import java.time.Duration;
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
 * Phase 03 硬性门槛：真实 PostgreSQL 上的库存并发与状态机测试。
 * - 多线程争抢不超卖（原子条件 UPDATE）；
 * - 预留 → 释放 / 确认 的幂等与竞态语义；
 * - 余额恒等式 available = total - reserved - sold。
 */
@SpringBootTest
class InventoryConcurrencyTests extends AbstractPostgresIntegrationTest {

    @Autowired
    InventoryFacade inventory;

    @Test
    void concurrentReserveDoesNotOversell() throws Exception {
        String poolCode = unique("oversell");
        inventory.createPool(new site.awn.inventory.api.CreatePoolCommand(poolCode,
                "prod-" + UUID.randomUUID().toString().substring(0, 8),
                "sess-" + UUID.randomUUID().toString().substring(0, 8), 10));

        int threads = 24;
        var latch = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(threads);
        var success = new AtomicInteger();
        var insufficient = new AtomicInteger();
        List<Future<?>> futures = new java.util.ArrayList<>();
        for (int i = 0; i < threads; i++) {
            final int seq = i;
            futures.add(executor.submit(() -> {
                try {
                    latch.await();
                    inventory.reserve(new ReserveInventoryCommand(poolCode, "order-" + seq, 1, null));
                    success.incrementAndGet();
                } catch (InsufficientInventoryException e) {
                    insufficient.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
        }
        latch.countDown();
        for (Future<?> f : futures) {
            f.get();
        }
        executor.shutdown();

        assertThat(success.get()).as("成功的预留数必须等于库存总量").isEqualTo(10);
        assertThat(insufficient.get()).as("其余请求必须拿到库存不足").isEqualTo(threads - 10);

        BalanceView balance = inventory.balanceOf(poolCode);
        assertThat(balance.reservedQuantity()).isEqualTo(10);
        assertThat(balance.soldQuantity()).isZero();
        assertThat(balance.available()).isZero();
    }

    @Test
    void concurrentAdjustmentsDoNotLoseUpdates() throws Exception {
        String poolCode = unique("adjust");
        inventory.createPool(new site.awn.inventory.api.CreatePoolCommand(poolCode,
                "prod-" + UUID.randomUUID().toString().substring(0, 8),
                "sess-" + UUID.randomUUID().toString().substring(0, 8), 100));

        // 16 线程各做 25 次 +1 调整；计数器必须原子累加，不允许读写改写丢失更新
        int threads = 16;
        int perThread = 25;
        var latch = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(threads);
        List<Future<?>> futures = new java.util.ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(() -> {
                try {
                    latch.await();
                    for (int j = 0; j < perThread; j++) {
                        inventory.adjustPool(poolCode, 1, "concurrent adjust", "test");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
        }
        latch.countDown();
        for (Future<?> f : futures) {
            f.get();
        }
        executor.shutdown();

        BalanceView balance = inventory.balanceOf(poolCode);
        assertThat(balance.totalQuantity())
                .as("400 次 +1 必须全部生效（不允许并发丢失更新）")
                .isEqualTo(100 + threads * perThread);
        assertThat(balance.reservedQuantity()).isZero();
        assertThat(balance.soldQuantity()).isZero();
        assertThat(balance.available()).isEqualTo(100 + threads * perThread);
    }

    @Test
    void confirmReleaseLifecycleIsGuardedAndIdempotent() {
        String poolCode = unique("lifecycle");
        inventory.createPool(new site.awn.inventory.api.CreatePoolCommand(poolCode,
                "prod-" + UUID.randomUUID().toString().substring(0, 8),
                "sess-" + UUID.randomUUID().toString().substring(0, 8), 5));

        var r1 = inventory.reserve(new ReserveInventoryCommand(poolCode, "order-1", 1, Duration.ofMinutes(5)));
        var r2 = inventory.reserve(new ReserveInventoryCommand(poolCode, "order-2", 2, null));

        assertThat(r1.quantity()).isEqualTo(1);
        assertThat(r1.expiresAt()).isNotNull();

        // 释放 r1（部分释放）
        assertThat(inventory.release(r1.reservationNo())).isTrue();
        // 重复释放：幂等重放，不重复回补
        assertThat(inventory.release(r1.reservationNo())).isFalse();
        // 确认 r2
        assertThat(inventory.confirm(r2.reservationNo())).isTrue();
        // 重复确认：幂等重放
        assertThat(inventory.confirm(r2.reservationNo())).isFalse();
        // 确认已释放的预留：状态机冲突，禁止静默
        assertThatThrownBy(() -> inventory.confirm(r1.reservationNo()))
                .isInstanceOf(InventoryConflictException.class);

        BalanceView balance = inventory.balanceOf(poolCode);
        assertThat(balance.reservedQuantity()).isZero();
        assertThat(balance.soldQuantity()).isEqualTo(2);
        assertThat(balance.available()).isEqualTo(3);
        // 恒等式
        assertThat(balance.totalQuantity() - balance.reservedQuantity() - balance.soldQuantity())
                .isEqualTo(balance.available());
    }

    @Test
    void reserveUnknownPoolFails() {
        assertThatThrownBy(() -> inventory.reserve(new ReserveInventoryCommand(unique("missing"), "order-x", 1, null)))
                .isInstanceOf(site.awn.inventory.domain.PoolNotFoundException.class);
    }

    private static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
