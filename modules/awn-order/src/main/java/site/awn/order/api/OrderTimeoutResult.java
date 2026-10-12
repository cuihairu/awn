package site.awn.order.api;

import java.util.List;

/**
 * 订单超时释放批处理结果。
 *
 * @param expired 本轮成功过期释放的订单数
 * @param skipped 因竞态已不可释放而跳过的订单数（如已支付、已取消）
 * @param failed 释放过程抛异常的订单号（进入告警，绝不静默吞掉）
 */
public record OrderTimeoutResult(int expired, int skipped, List<String> failed) {

    public OrderTimeoutResult {
        failed = failed == null ? List.of() : List.copyOf(failed);
    }
}
