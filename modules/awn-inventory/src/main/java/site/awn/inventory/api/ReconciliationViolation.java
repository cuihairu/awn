package site.awn.inventory.api;

/**
 * 库存对账差异项。
 *
 * @param poolCode 库存池
 * @param kind     差异类型：RESERVED_MISMATCH / SOLD_MISMATCH
 * @param expected 按预留记录推导的期望值
 * @param actual   余额计数器实际值
 */
public record ReconciliationViolation(String poolCode, String kind, long expected, long actual) {
}
