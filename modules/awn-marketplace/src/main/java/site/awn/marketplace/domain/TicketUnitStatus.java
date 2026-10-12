package site.awn.marketplace.domain;

/** 独立票品状态：与挂牌解耦，票品有自己的生命周期与交付跟踪 */
public enum TicketUnitStatus {
    IN_STOCK,
    RESERVED,
    SOLD,
    DELIVERED,
    INVALID;

    /** 状态迁移是否合法（非法迁移一律拒绝） */
    public boolean canTransitionTo(TicketUnitStatus target) {
        return switch (this) {
            case IN_STOCK -> target == RESERVED || target == SOLD || target == INVALID;
            case RESERVED -> target == SOLD || target == IN_STOCK || target == INVALID;
            case SOLD -> target == DELIVERED;
            case DELIVERED, INVALID -> false;
        };
    }
}
