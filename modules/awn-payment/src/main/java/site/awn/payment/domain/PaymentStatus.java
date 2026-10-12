package site.awn.payment.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 支付状态机（docs/architecture/payment-state-machine.md）。
 * 支付状态独立于订单状态管理，禁止混用。
 */
public enum PaymentStatus {

    CREATED,
    PENDING,
    SUCCEEDED,
    FAILED,
    UNKNOWN,
    REFUNDING,
    PARTIALLY_REFUNDED,
    REFUNDED;

    private static final Map<PaymentStatus, Set<PaymentStatus>> ALLOWED = Map.of(
            CREATED, EnumSet.of(PENDING, SUCCEEDED, FAILED, UNKNOWN),
            PENDING, EnumSet.of(SUCCEEDED, FAILED, UNKNOWN),
            SUCCEEDED, EnumSet.of(REFUNDING, PARTIALLY_REFUNDED, REFUNDED),
            FAILED, EnumSet.noneOf(PaymentStatus.class),
            UNKNOWN, EnumSet.of(SUCCEEDED, FAILED),
            REFUNDING, EnumSet.of(PARTIALLY_REFUNDED, REFUNDED),
            PARTIALLY_REFUNDED, EnumSet.of(PARTIALLY_REFUNDED, REFUNDED),
            REFUNDED, EnumSet.noneOf(PaymentStatus.class));

    public boolean isTerminal() {
        return ALLOWED.get(this).isEmpty();
    }

    public boolean canTransitionTo(PaymentStatus target) {
        return ALLOWED.get(this).contains(target);
    }

    /** 允许接收支付成功回调的状态 */
    public boolean canAcceptSuccess() {
        return this == CREATED || this == PENDING || this == UNKNOWN;
    }
}
