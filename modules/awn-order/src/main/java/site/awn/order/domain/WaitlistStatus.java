package site.awn.order.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 候补席位状态机（docs/api/contracts.md「排队 / 候补」）。
 * 排队中的席位不占库存；递补成功才生成待支付订单，席位与订单状态联动收口。
 */
public enum WaitlistStatus {

    /** 排队中（占位，不占库存） */
    WAITING,
    /** 已递补：已原子预留并生成待支付订单 */
    PROMOTED,
    /** 席位已成单（订单已支付） */
    CLAIMED,
    /** 递补后未在支付截止期内支付，席位过期 */
    EXPIRED,
    /** 主动退出排队 */
    CANCELLED;

    private static final Map<WaitlistStatus, Set<WaitlistStatus>> ALLOWED = Map.of(
            WAITING, EnumSet.of(PROMOTED, CANCELLED),
            PROMOTED, EnumSet.of(CLAIMED, EXPIRED),
            CLAIMED, EnumSet.noneOf(WaitlistStatus.class),
            EXPIRED, EnumSet.noneOf(WaitlistStatus.class),
            CANCELLED, EnumSet.noneOf(WaitlistStatus.class));

    public boolean isTerminal() {
        return ALLOWED.get(this).isEmpty();
    }

    public boolean canTransitionTo(WaitlistStatus target) {
        return ALLOWED.get(this).contains(target);
    }

    /** 是否仍占位（同用户同池不可重复排队） */
    public boolean occupies() {
        return this == WAITING || this == PROMOTED;
    }
}