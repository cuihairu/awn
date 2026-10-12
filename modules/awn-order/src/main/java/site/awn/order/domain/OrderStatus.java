package site.awn.order.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 订单状态机（docs/architecture/payment-state-machine.md）。
 * 订单状态不兼任支付状态与履约状态，三者独立管理。
 */
public enum OrderStatus {

    PENDING_PAYMENT,
    PAID,
    FULFILLING,
    FULFILLED,
    REFUND_PENDING,
    REFUNDED,
    CANCELLED,
    EXPIRED;

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED = Map.of(
            PENDING_PAYMENT, EnumSet.of(PAID, CANCELLED, EXPIRED),
            PAID, EnumSet.of(FULFILLING, REFUND_PENDING),
            FULFILLING, EnumSet.of(FULFILLED, REFUND_PENDING),
            FULFILLED, EnumSet.of(REFUND_PENDING),
            REFUND_PENDING, EnumSet.of(REFUNDED, PAID),
            REFUNDED, EnumSet.noneOf(OrderStatus.class),
            CANCELLED, EnumSet.noneOf(OrderStatus.class),
            EXPIRED, EnumSet.noneOf(OrderStatus.class));

    /** 终态：REFUNDED / CANCELLED / EXPIRED */
    public boolean isTerminal() {
        return ALLOWED.get(this).isEmpty();
    }

    /** 迁移合法性由本表唯一定义，任何更新路径都必须先经它守卫 */
    public boolean canTransitionTo(OrderStatus target) {
        return ALLOWED.get(this).contains(target);
    }
}
