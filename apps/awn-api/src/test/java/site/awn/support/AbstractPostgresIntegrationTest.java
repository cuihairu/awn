package site.awn.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * 集成测试基类：真实 PostgreSQL，双模式取数。
 *
 * - 默认（CI / docker-java 兼容环境）：Testcontainers 自动起 postgres:16-alpine；
 * - 本机（Docker Engine 29+ 拒绝旧客户端 API 1.32 时）：设置
 *   AWN_TEST_DB_URL / AWN_TEST_DB_USER / AWN_TEST_DB_PASSWORD 指向手动启动的
 *   PostgreSQL 容器，例如：
 *   docker run -d --rm --name awn-test-pg -p 55433:5432 \
 *     -e POSTGRES_USER=awn -e POSTGRES_PASSWORD=awn -e POSTGRES_DB=awn postgres:16-alpine
 *   AWN_TEST_DB_URL=jdbc:postgresql://localhost:55433/awn
 *
 * 两种模式下执行的是同一套测试与同一套迁移。
 */
public abstract class AbstractPostgresIntegrationTest {

    private static final String EXTERNAL_URL = System.getenv("AWN_TEST_DB_URL");

    private static PostgreSQLContainer<?> POSTGRES;

    static {
        if (EXTERNAL_URL == null) {
            POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
        }
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        if (EXTERNAL_URL != null) {
            registry.add("spring.datasource.url", () -> EXTERNAL_URL);
            registry.add("spring.datasource.username", () -> System.getenv("AWN_TEST_DB_USER"));
            registry.add("spring.datasource.password", () -> System.getenv("AWN_TEST_DB_PASSWORD"));
        } else {
            POSTGRES.start();
            registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
            registry.add("spring.datasource.username", POSTGRES::getUsername);
            registry.add("spring.datasource.password", POSTGRES::getPassword);
        }
        // 所有测试上下文共享同一个 PostgreSQL 容器，而 Hikari 按 maximum-pool-size 预热连接：
        // 主配置的 20 × 多个缓存上下文（@TestConfiguration / 不同探针配置各成一份）
        // 会打满容器的 max_connections，报 PSQL FATAL: too many clients already。
        // 测试为单线程顺序执行，6 个连接足够；收紧池上限而不是抬高容器连接数。
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "6");
        registry.add("spring.datasource.hikari.minimum-idle", () -> "1");
    }
}
