package site.awn.payment.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 退款状态机（docs/architecture/payment-state-machine.md）。
 * 渠道结果未知时先查询，不盲目再次退款。
 */
public enum RefundStatus {

    CREATED,
    PENDING,
    SUCCEEDED,
    FAILED,
    UNKNOWN;

    private static final Map<RefundStatus, Set<RefundStatus>> ALLOWED = Map.of(
            CREATED, EnumSet.of(PENDING, SUCCEEDED, FAILED),
            PENDING, EnumSet.of(SUCCEEDED, FAILED, UNKNOWN),
            SUCCEEDED, EnumSet.noneOf(RefundStatus.class),
            FAILED, EnumSet.noneOf(RefundStatus.class),
            UNKNOWN, EnumSet.of(SUCCEEDED, FAILED));

    public boolean isTerminal() {
        return ALLOWED.get(this).isEmpty();
    }

    public boolean canTransitionTo(RefundStatus target) {
        return ALLOWED.get(this).contains(target);
    }
}
