package site.awn.inventory;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import site.awn.inventory.api.CreatePoolCommand;
import site.awn.inventory.api.InventoryFacade;
import site.awn.inventory.api.ReconciliationReport;
import site.awn.support.AbstractPostgresIntegrationTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 压测补充：对账必须覆盖 total 与 ADJUST 流水。
 * 调整接口曾经的读改写丢失更新（台账记满、计数器没跟上）不会被 reserved / sold 口径发现。
 */
@SpringBootTest
class InventoryReconciliationTests extends AbstractPostgresIntegrationTest {

    @Autowired
    InventoryFacade inventory;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    site.awn.support.CatalogProductFactory products;

    @Test
    void createPoolWritesBootstrapLedgerRow() {
        String poolCode = unique("ledger");
        int total = 10;
        inventory.createPool(new CreatePoolCommand(poolCode,
                products.productCode(poolCode),
                "sess-" + UUID.randomUUID().toString().substring(0, 8), total));

        Long ledgerSum = jdbc.queryForObject(
                "SELECT COALESCE(SUM(t.quantity), 0) FROM inventory_transaction t "
                        + "JOIN inventory_pool p ON p.id = t.inventory_pool_id "
                        + "WHERE p.pool_code = ? AND t.type = 'ADJUST'",
                Long.class, poolCode);
        assertThat(ledgerSum).as("建池必须留一条 bootstrap ADJUST 流水，使流水可复原总量").isEqualTo(total);

        assertThat(inventory.reconcile().violations())
                .noneMatch(v -> "TOTAL_MISMATCH".equals(v.kind()) && poolCode.equals(v.poolCode()));
    }

    @Test
    void totalLedgerDriftIsReported() {
        String poolCode = unique("drift");
        inventory.createPool(new CreatePoolCommand(poolCode,
                products.productCode(poolCode),
                "sess-" + UUID.randomUUID().toString().substring(0, 8), 10));

        // 模拟越权或丢失更新的计数器漂移（绕过程序直接改表）
        jdbc.update("UPDATE inventory_balance SET total_quantity = total_quantity + 5 "
                + "WHERE inventory_pool_id = (SELECT id FROM inventory_pool WHERE pool_code = ?)", poolCode);

        ReconciliationReport report = inventory.reconcile();
        assertThat(report.violations())
                .anyMatch(v -> "TOTAL_MISMATCH".equals(v.kind())
                        && poolCode.equals(v.poolCode())
                        && v.expected() == 10 && v.actual() == 15);
    }

    private static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
