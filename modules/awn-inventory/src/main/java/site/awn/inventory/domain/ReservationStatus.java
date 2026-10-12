package site.awn.inventory.domain;

/**
 * 预留状态机：ACTIVE → CONFIRMED（支付确认）或 ACTIVE → RELEASED（取消 / 超时）。
 * 状态迁移必须走带状态守卫的 UPDATE，禁止无条件覆盖。
 */
public enum ReservationStatus {
    ACTIVE,
    CONFIRMED,
    RELEASED
}
