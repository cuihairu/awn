package site.awn.shared.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 进程内限流：窗口内放行上限、key 独立、窗口过期复原、并发不超限。
 */
class InMemoryRateLimiterTest {

    private final InMemoryRateLimiter limiter = new InMemoryRateLimiter();

    @Test
    void allowsUpToLimitWithinWindowAndDeniesBeyond() {
        for (int i = 0; i < 3; i++) {
            assertThat(limiter.allow("k", 3, Duration.ofSeconds(60))).isTrue();
        }
        assertThat(limiter.allow("k", 3, Duration.ofSeconds(60)))
                .as("第 maxRequests+1 次必须拒绝").isFalse();
    }

    @Test
    void windowsAreIndependentPerKey() {
        assertThat(limiter.allow("a", 1, Duration.ofSeconds(60))).isTrue();
        assertThat(limiter.allow("a", 1, Duration.ofSeconds(60))).isFalse();
        assertThat(limiter.allow("b", 1, Duration.ofSeconds(60))).isTrue();
    }

    @Test
    void expiredWindowResets() throws InterruptedException {
        assertThat(limiter.allow("k", 1, Duration.ofMillis(50))).isTrue();
        assertThat(limiter.allow("k", 1, Duration.ofMillis(50))).isFalse();
        Thread.sleep(80);
        assertThat(limiter.allow("k", 1, Duration.ofMillis(50)))
                .as("窗口整体过期后额度复原").isTrue();
    }

    @Test
    void concurrentCallsNeverExceedLimit() throws InterruptedException {
        int threads = 10;
        int perThread = 10;
        int limit = 50;
        AtomicInteger allowed = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < perThread; i++) {
                        if (limiter.allow("shared", limit, Duration.ofSeconds(60))) {
                            allowed.incrementAndGet();
                        } else {
                            rejected.incrementAndGet();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        assertThat(allowed.get()).isEqualTo(limit);
        assertThat(rejected.get()).isEqualTo(threads * perThread - limit);
    }
}
