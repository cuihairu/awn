package site.awn.inventory;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import site.awn.inventory.api.BalanceView;
import site.awn.inventory.api.InventoryFacade;
import site.awn.inventory.api.ReconciliationReport;
import site.awn.inventory.api.ReserveInventoryCommand;
import site.awn.inventory.domain.InsufficientInventoryException;
import site.awn.support.AbstractPostgresIntegrationTest;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 03 收尾验收：并发压测 + 一致性断言。
 * 验收口径（todo Phase 03）：
 * - 压测后 available = total - reserved - sold 恒等式成立；
 * - 超卖次数为 0（可用量任何时刻不为负）；
 * - 预留记录 ↔ 余额计数器对账一致。
 */
@SpringBootTest
class InventoryStressTests extends AbstractPostgresIntegrationTest {

    @Autowired
    InventoryFacade inventory;

    @Test
    void mixedWorkloadKeepsInvariantAndNeverOversells() throws Exception {
        String poolCode = unique("stress");
        int total = 50;
        inventory.createPool(new site.awn.inventory.api.CreatePoolCommand(poolCode,
                "prod-" + UUID.randomUUID().toString().substring(0, 8),
                "sess-" + UUID.randomUUID().toString().substring(0, 8), total));

        int threads = 32;
        int roundsPerThread = 12;
        var latch = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(threads + 1);

        // 监视线程：压测全程读取余额，任何时刻可用量不得为负（超卖次数为 0）
        var oversellObserved = new AtomicBoolean();
        var monitor = executor.submit(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    BalanceView b = inventory.balanceOf(poolCode);
                    if (b.available() < 0 || b.reservedQuantity() < 0 || b.soldQuantity() < 0) {
                        oversellObserved.set(true);
                    }
                    if (b.reservedQuantity() + b.soldQuantity() > b.totalQuantity()) {
                        oversellObserved.set(true);
                    }
                } catch (Exception e) {
                    // 池刚创建或读取竞态：忽略，下轮再读
                }
                Thread.yield();
            }
        });

        var reserved = new AtomicInteger();
        var insufficient = new AtomicInteger();
        var confirmed = new AtomicInteger();
        var released = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();

        for (int t = 0; t < threads; t++) {
            final int seq = t;
            futures.add(executor.submit(() -> {
                try {
                    latch.await();
                    for (int r = 0; r < roundsPerThread; r++) {
                        String orderNo = "order-" + seq + "-" + r;
                        try {
                            var reservation = inventory.reserve(new ReserveInventoryCommand(
                                    poolCode, orderNo, 1, Duration.ofMinutes(10)));
                            reserved.incrementAndGet();

                            // 预留成功后随机走向：30% 确认 / 30% 释放 / 40% 保持 ACTIVE
                            int dice = ThreadLocalRandom.current().nextInt(10);
                            if (dice < 3) {
                                if (inventory.confirm(reservation.reservationNo())) {
                                    confirmed.incrementAndGet();
                                }
                            } else if (dice < 6) {
                                if (inventory.release(reservation.reservationNo())) {
                                    released.incrementAndGet();
                                }
                            }
                        } catch (InsufficientInventoryException e) {
                            insufficient.incrementAndGet();
                        }
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
        monitor.cancel(true);
        executor.shutdown();

        // 请求守恒：每次预留要么成功要么库存不足，无其他出路
        assertThat(reserved.get() + insufficient.get())
                .as("请求数守恒").isEqualTo((long) threads * roundsPerThread);
        // 确认 + 释放不超预留数（幂等重放不计入第二次）
        assertThat(confirmed.get() + released.get()).isLessThanOrEqualTo(reserved.get());

        // 超卖次数为 0：压测全程余额无负值、总量未被打穿
        assertThat(oversellObserved.get()).as("压测期间不允许出现超卖").isFalse();

        // 压测后恒等式：available = total - reserved - sold
        BalanceView balance = inventory.balanceOf(poolCode);
        assertThat(balance.available())
                .as("available = total - reserved - sold")
                .isEqualTo(balance.totalQuantity() - balance.reservedQuantity() - balance.soldQuantity());
        assertThat(balance.reservedQuantity() + balance.soldQuantity()).isLessThanOrEqualTo(total);
        assertThat(balance.soldQuantity()).isEqualTo(confirmed.get());

        // 对账：本池预留记录 ↔ 余额计数器 ↔ ADJUST 流水账实相符
        // （共享测试库留存历史池的旧数据，全局 consistent() 不能作为本用例口径）
        ReconciliationReport report = inventory.reconcile();
        assertThat(report.violations())
                .filteredOn(v -> poolCode.equals(v.poolCode()))
                .as("压测池对账应无差异")
                .isEmpty();
    }

    private static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
