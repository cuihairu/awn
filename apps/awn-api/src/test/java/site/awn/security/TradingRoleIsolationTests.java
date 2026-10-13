package site.awn.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import site.awn.support.AbstractPostgresIntegrationTest;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 交易角色最小权限验收（V12__trading_role.sql）。
 * 验收口径（todo 交易角色最小权限化）：
 * - awn_trading 对交易表组（public）具备完整读写，含身份列与 updated_at 触发器函数；
 * - 不具备 CREATE（不能新增表），即最小权限而非"可自建对象"；
 * - 对情报 schema 零权限：读、写、建表全部拒绝。
 * 迁移已由 Flyway 以应用账号执行，无需测试侧补建角色。
 * 边界性质与 awn_intel 相同——权限边界，不是物理隔离。
 */
@SpringBootTest
class TradingRoleIsolationTests extends AbstractPostgresIntegrationTest {

    private static final String TRADING_USER = "awn_trading";
    private static final String TRADING_PASSWORD = "change-me-trading-password";

    @Value("${spring.datasource.url}") String dbUrl;

    @Test
    void tradingRoleHasTradingAccessOnly() throws Exception {
        String poolCode = "iso-" + UUID.randomUUID().toString().substring(0, 8);

        try (Connection app = DriverManager.getConnection(dbUrl, TRADING_USER, TRADING_PASSWORD)) {
            // 交易表：完整读写（身份列 INSERT + updated_at 触发器 UPDATE）
            try (Statement st = app.createStatement()) {
                st.execute("INSERT INTO inventory_pool (pool_code, product_id, session_id) "
                        + "VALUES ('" + poolCode + "', 'p-iso', 's-iso')");
                st.execute("UPDATE inventory_pool SET product_id = 'p-iso2' WHERE pool_code = '" + poolCode + "'");
                try (ResultSet rs = st.executeQuery(
                        "SELECT product_id FROM inventory_pool WHERE pool_code = '" + poolCode + "'")) {
                    rs.next();
                    assertThat(rs.getString(1)).as("awn_trading 必须能读交易表").isEqualTo("p-iso2");
                }
                st.execute("DELETE FROM inventory_pool WHERE pool_code = '" + poolCode + "'");
            }

            // 无 CREATE 权限：不能自建对象
            try (Statement st = app.createStatement()) {
                assertThatThrownBy(() -> st.execute(
                                "CREATE TABLE iso_probe_" + UUID.randomUUID().toString().substring(0, 8)
                                        + " (id int)"))
                        .as("awn_trading 在交易 schema 建表必须被拒绝")
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("denied");
            }

            // 情报 schema：读、写、建表全部拒绝
            try (Statement st = app.createStatement()) {
                assertThatThrownBy(() -> st.executeQuery(
                                "SELECT source_no FROM intelligence.data_sources LIMIT 1"))
                        .as("awn_trading 读情报表必须被拒绝")
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("denied");
                assertThatThrownBy(() -> st.execute(
                                "INSERT INTO intelligence.data_sources (source_no, name, kind, trust_tier, enabled) "
                                        + "VALUES ('SRC-PROBE', '探测', 'OFFICIAL_PUBLISHED', 1, true)"))
                        .as("awn_trading 写情报表必须被拒绝")
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("denied");
                assertThatThrownBy(() -> st.execute(
                                "CREATE TABLE intelligence.iso_probe_" + UUID.randomUUID().toString().substring(0, 8)
                                        + " (id int)"))
                        .as("awn_trading 在情报 schema 建表必须被拒绝")
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("denied");
            }
        }
    }
}
