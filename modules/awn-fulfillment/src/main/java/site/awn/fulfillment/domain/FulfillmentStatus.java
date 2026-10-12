package site.awn.fulfillment.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 履约状态（docs/architecture/domain-model.md）：独立于订单状态与支付状态。
 * 支付成功只把履约推进到 PENDING_SELLER——支付成功不等于交付成功。
 */
public enum FulfillmentStatus {

    NOT_STARTED,
    PENDING_SELLER,
    PENDING_VERIFICATION,
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    CANCELLED;

    private static final Map<FulfillmentStatus, Set<FulfillmentStatus>> ALLOWED = Map.of(
            NOT_STARTED, EnumSet.of(PENDING_SELLER, CANCELLED),
            PENDING_SELLER, EnumSet.of(PENDING_VERIFICATION, IN_PROGRESS, FAILED, CANCELLED),
            PENDING_VERIFICATION, EnumSet.of(COMPLETED, FAILED),
            IN_PROGRESS, EnumSet.of(PENDING_VERIFICATION, COMPLETED, FAILED, CANCELLED),
            COMPLETED, EnumSet.noneOf(FulfillmentStatus.class),
            FAILED, EnumSet.noneOf(FulfillmentStatus.class),
            CANCELLED, EnumSet.noneOf(FulfillmentStatus.class));

    /** 终态 */
    public boolean isTerminal() {
        return ALLOWED.get(this).isEmpty();
    }

    /** 迁移是否合法（非法迁移一律拒绝，留痕进异常流程） */
    public boolean canTransitionTo(FulfillmentStatus target) {
        return ALLOWED.get(this).contains(target);
    }
}
