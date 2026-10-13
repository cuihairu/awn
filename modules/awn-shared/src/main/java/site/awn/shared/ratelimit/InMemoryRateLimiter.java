package site.awn.shared.ratelimit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进程内固定窗口限流（默认实现，awn.ratelimit.store=memory 或缺省）。
 * 仅适合单实例或开发/测试环境；多实例部署请用 Redis 实现（store=redis）。
 * 注意：key 只在新窗口替换时不清理，key 数量应保持有限（如按资源组而非按用户）。
 */
@Component
@ConditionalOnProperty(prefix = "awn.ratelimit", name = "store", havingValue = "memory", matchIfMissing = true)
public class InMemoryRateLimiter implements RateLimiter {

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    @Override
    public boolean allow(String key, int maxRequests, Duration window) {
        long now = System.nanoTime();
        long windowNanos = window.toNanos();
        Window current = windows.compute(key, (k, old) ->
                old == null || now - old.startedAt >= windowNanos ? new Window(now) : old);
        synchronized (current) {
            if (now - current.startedAt >= windowNanos) {
                current.startedAt = now;
                current.count = 0;
            }
            current.count++;
            return current.count <= maxRequests;
        }
    }

    private static final class Window {

        private long startedAt;
        private int count;

        private Window(long startedAt) {
            this.startedAt = startedAt;
        }
    }
}
