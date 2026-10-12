package site.awn.inventory.api;

import java.util.List;

/**
 * 库存对账报告：预留记录 ↔ 余额计数器逐池核对。
 * violations 为空表示账实相符。
 */
public record ReconciliationReport(int poolsChecked, List<ReconciliationViolation> violations) {

    public ReconciliationReport {
        violations = violations == null ? List.of() : List.copyOf(violations);
    }

    public boolean consistent() {
        return violations.isEmpty();
    }
}
