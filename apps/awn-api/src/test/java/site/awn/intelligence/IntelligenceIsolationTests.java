package site.awn.intelligence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import site.awn.support.AbstractPostgresIntegrationTest;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 06：情报库权限边界验收（同实例 intelligence schema + 独立角色 awn_intel，
 * 非"物理隔离"；见 docs/architecture/intelligence-isolation.md）。
 * 用 deploy/config/postgres/init-intel.sql 初始化最小权限角色 awn_intel，
 * 再以该角色直连数据库验证：intelligence schema 可读写，任何交易表不可触碰。
 */
@SpringBootTest
class IntelligenceIsolationTests extends AbstractPostgresIntegrationTest {

    private static final String INTEL_USER = "awn_intel";
    private static final String INTEL_PASSWORD = "change-me-intel-password";

    @Autowired DataSource dataSource;
    @Value("${spring.datasource.url}") String dbUrl;

    @Test
    void intelRoleHasFullAccessToIntelligenceSchemaOnly() throws Exception {
        applyInitScriptAsOwner();

        try (Connection intel = DriverManager.getConnection(dbUrl, INTEL_USER, INTEL_PASSWORD)) {
            // 情报 schema：完整读写
            String sourceNo = "SRC-ISO-" + UUID.randomUUID().toString().substring(0, 8);
            try (Statement st = intel.createStatement()) {
                st.execute("INSERT INTO intelligence.data_sources (source_no, name, kind, trust_tier, enabled) "
                        + "VALUES ('" + sourceNo + "', '隔离验证来源', 'OFFICIAL_PUBLISHED', 1, true)");
                ResultSet rs = st.executeQuery(
                        "SELECT count(*) FROM intelligence.data_sources WHERE source_no = '" + sourceNo + "'");
                rs.next();
                assertThat(rs.getLong(1)).as("awn_intel 必须能写 intelligence schema").isEqualTo(1);
                st.execute("UPDATE intelligence.data_sources SET enabled = false WHERE source_no = '" + sourceNo + "'");
                st.execute("DELETE FROM intelligence.data_sources WHERE source_no = '" + sourceNo + "'");
            }

            // 交易库：读、写、建表全部拒绝（schema 级 REVOKE）
            try (Statement st = intel.createStatement()) {
                assertThatThrownBy(() -> st.executeQuery("SELECT order_no FROM public.orders LIMIT 1"))
                        .as("awn_intel 读交易表必须被拒绝")
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("denied");
                assertThatThrownBy(() -> st.execute("SELECT 1 FROM public.orders FOR UPDATE"))
                        .as("awn_intel 锁交易表必须被拒绝")
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("denied");
                assertThatThrownBy(() -> st.execute("CREATE TABLE public.iso_probe_" + UUID.randomUUID()
                        .toString().substring(0, 8) + " (id int)"))
                        .as("awn_intel 在交易 schema 建表必须被拒绝")
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("denied");
            }
        }
    }

    /** 以库属主（测试库超级用户）身份执行 init-intel.sql：幂等，可重复执行。
     *  脚本内密码为 psql 变量（:'intel_password'，部署由 init-intel.sh 注入），
     *  JDBC 不能解析 psql 变量，执行前替换为测试常量。 */
    private void applyInitScriptAsOwner() throws Exception {
        Path script = locate("deploy/config/postgres/init-intel.sql");
        String sql = Files.readString(script, StandardCharsets.UTF_8)
                .replace(":'intel_password'", "'" + INTEL_PASSWORD + "'");
        try (Connection owner = dataSource.getConnection(); Statement st = owner.createStatement()) {
            st.execute(sql);
        }
    }

    private Path locate(String relative) {
        List<Path> candidates = List.of(
                Path.of("..", "..", relative),   // surefire 工作目录 = apps/awn-api
                Path.of(relative));              // 工作目录 = 仓库根
        return candidates.stream().filter(Files::exists).findFirst()
                .orElseThrow(() -> new IllegalStateException("init script not found: " + relative));
    }
}
