package site.awn.order.api;

import site.awn.order.domain.DisputeReason;
import site.awn.order.domain.DisputeRequestType;

/**
 * 开争议命令。语义守卫（订单归属 / 可争议状态 / 在途判重 / 金额上限）由模块层裁决：
 * 仅订单本人可开；订单须 PAID / FULFILLING / FULFILLED；REFUND 必填金额且 ≤ 应付，
 * REDELIVER 不得带金额；evidenceUrl 过受控存储域白名单。
 */
public record OpenDisputeCommand(
        String orderNo,
        Long userId,
        DisputeReason reason,
        String description,
        String evidenceUrl,
        DisputeRequestType requestedResolution,
        Long requestedAmount) {
}
