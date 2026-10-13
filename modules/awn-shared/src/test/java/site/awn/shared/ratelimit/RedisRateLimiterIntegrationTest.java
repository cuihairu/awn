package site.awn.shared.ratelimit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Redis 限流真实容器验收（redis:7-alpine）：窗口内放行上限、窗口过期后额度复原。
 * 测试库不复用 Redis，key 仍加随机段避免同机并行互相干扰。
 */
class RedisRateLimiterIntegrationTest {

    private static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;

    @BeforeAll
    static void startRedis() {
        REDIS.start();
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
    }

    @AfterAll
    static void stopRedis() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
        REDIS.stop();
    }

    @Test
    void allowsUpToLimitAndDeniesBeyond() {
        RateLimiter limiter = new RedisRateLimiter(redisTemplate);
        String key = "it-" + UUID.randomUUID();

        for (int i = 0; i < 3; i++) {
            assertThat(limiter.allow(key, 3, Duration.ofSeconds(60))).isTrue();
        }
        assertThat(limiter.allow(key, 3, Duration.ofSeconds(60)))
                .as("第 maxRequests+1 次必须拒绝").isFalse();
    }

    @Test
    void expiredWindowResetsOnRedisSide() throws InterruptedException {
        RateLimiter limiter = new RedisRateLimiter(redisTemplate);
        String key = "it-" + UUID.randomUUID();

        assertThat(limiter.allow(key, 1, Duration.ofSeconds(2))).isTrue();
        assertThat(limiter.allow(key, 1, Duration.ofSeconds(2))).isFalse();
        Thread.sleep(2500);
        assertThat(limiter.allow(key, 1, Duration.ofSeconds(2)))
                .as("Redis 侧窗口过期后额度复原").isTrue();
    }

    @Test
    void keysAreIndependent() {
        RateLimiter limiter = new RedisRateLimiter(redisTemplate);
        String keyA = "it-" + UUID.randomUUID();
        String keyB = "it-" + UUID.randomUUID();

        assertThat(limiter.allow(keyA, 1, Duration.ofSeconds(60))).isTrue();
        assertThat(limiter.allow(keyA, 1, Duration.ofSeconds(60))).isFalse();
        assertThat(limiter.allow(keyB, 1, Duration.ofSeconds(60))).isTrue();
    }
}
