package site.awn;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Phase 00 地基测试：真实 PostgreSQL（Testcontainers）上完成
 * Flyway 迁移执行 + JPA schema 校验 + 上下文装配。
 *
 * Redis 连接为惰性建立，本地/CI 无需真实 Redis。
 */
@SpringBootTest
@Testcontainers
class AwnApiApplicationTests {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void contextLoadsWithFlywayMigrations() {
        // 启动即证明：V1__baseline.sql 可执行、ddl-auto: validate 通过
    }
}
