package site.awn.order.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 争议单状态机（docs/architecture/dispute-handling.md）。
 * 受理与裁决是两个动作：OPEN → UNDER_REVIEW 表示平台接手（此后买家不能单方面撤回），
 * UNDER_REVIEW → RESOLVED 给出裁决结论并留痕。
 */
public enum DisputeStatus {

    OPEN,
    UNDER_REVIEW,
    WITHDRAWN,
    RESOLVED;

    private static final Map<DisputeStatus, Set<DisputeStatus>> ALLOWED = Map.of(
            OPEN, EnumSet.of(UNDER_REVIEW, WITHDRAWN),
            UNDER_REVIEW, EnumSet.of(RESOLVED),
            WITHDRAWN, EnumSet.noneOf(DisputeStatus.class),
            RESOLVED, EnumSet.noneOf(DisputeStatus.class));

    /** 终态：WITHDRAWN / RESOLVED */
    public boolean isTerminal() {
        return ALLOWED.get(this).isEmpty();
    }

    /** 迁移合法性由本表唯一定义，任何更新路径都必须先经它守卫 */
    public boolean canTransitionTo(DisputeStatus target) {
        return ALLOWED.get(this).contains(target);
    }
}
