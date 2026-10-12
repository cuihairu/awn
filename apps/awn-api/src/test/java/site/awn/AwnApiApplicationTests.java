package site.awn;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import site.awn.support.AbstractPostgresIntegrationTest;

/**
 * Phase 00 地基测试：真实 PostgreSQL 上完成
 * Flyway 迁移执行 + JPA schema 校验 + 上下文装配。
 *
 * Redis 连接为惰性建立，本地/CI 无需真实 Redis。
 */
@SpringBootTest
class AwnApiApplicationTests extends AbstractPostgresIntegrationTest {

    @Test
    void contextLoadsWithFlywayMigrations() {
        // 启动即证明：V1/V2 迁移可执行、ddl-auto: validate 通过
    }
}
