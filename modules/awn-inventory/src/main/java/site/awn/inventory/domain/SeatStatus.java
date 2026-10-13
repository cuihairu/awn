package site.awn.inventory.domain;

/**
 * 平台座位状态机：IN_STOCK → RESERVED → SOLD → DELIVERED，INVALID 为作废终态。
 * 与挂牌票品（ticket_units）语义无关：座位是平台自营的物理位置，不是卖家的转售票。
 * SOLD 只能从 RESERVED 来：卖出必经预留，占座订单号（order_no）才有落点，杜绝无主售出。
 */
public enum SeatStatus {
    IN_STOCK,
    RESERVED,
    SOLD,
    DELIVERED,
    INVALID;

    /** 状态迁移是否合法（非法迁移一律拒绝） */
    public boolean canTransitionTo(SeatStatus target) {
        return switch (this) {
            case IN_STOCK -> target == RESERVED || target == INVALID;
            case RESERVED -> target == SOLD || target == IN_STOCK || target == INVALID;
            case SOLD -> target == DELIVERED;
            case DELIVERED, INVALID -> false;
        };
    }
}
