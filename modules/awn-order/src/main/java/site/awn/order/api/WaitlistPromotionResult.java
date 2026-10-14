package site.awn.order.api;

import java.util.List;

/**
 * 一轮递补扫描的结果（契约「排队 / 候补」）。
 *
 * @param promoted 本轮新递补的席位数（WAITING → PROMOTED，已生成待支付订单）
 * @param claimed  本轮确认成单的席位数（PROMOTED → CLAIMED，订单已支付）
 * @param expired  本轮过期释放的席位数（PROMOTED → EXPIRED，订单超时或取消）
 * @param failed   本轮递补失败的候补编号（单条异常不影响其余条目）
 */
public record WaitlistPromotionResult(int promoted, int claimed, int expired, List<String> failed) {

    public WaitlistPromotionResult {
        failed = failed == null ? List.of() : List.copyOf(failed);
    }
}
