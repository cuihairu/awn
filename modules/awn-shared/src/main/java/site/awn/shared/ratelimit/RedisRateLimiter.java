package site.awn.shared.ratelimit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Redis 固定窗口限流（awn.ratelimit.store=redis）：多实例共享额度。
 * 计数用 INCR，窗口起点首次计数时挂 EXPIRE，窗口整体过期后额度复原。
 */
@Component
@ConditionalOnProperty(prefix = "awn.ratelimit", name = "store", havingValue = "redis")
public class RedisRateLimiter implements RateLimiter {

    private static final String KEY_PREFIX = "awn:ratelimit:";

    private final StringRedisTemplate redis;

    public RedisRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public boolean allow(String key, int maxRequests, Duration window) {
        String redisKey = KEY_PREFIX + key;
        Long count = redis.opsForValue().increment(redisKey);
        if (count != null && count == 1L) {
            redis.expire(redisKey, window);
        }
        return count != null && count <= maxRequests;
    }
}
