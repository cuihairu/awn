package site.awn.order.api;

import site.awn.order.domain.DisputeResolution;

/**
 * 平台裁决命令：UNDER_REVIEW → RESOLVED，结论与 note 一次落定。
 * 裁决只记录结论，不动钱——退款执行走支付模块既有退款流，
 * 重新交付走履约面（docs/architecture/dispute-handling.md「裁决权限」）。
 */
public record AdjudicateDisputeCommand(
        String disputeNo,
        String actor,
        DisputeResolution resolution,
        String note) {
}
