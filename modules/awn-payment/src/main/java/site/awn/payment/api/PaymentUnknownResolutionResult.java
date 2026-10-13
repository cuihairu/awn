package site.awn.payment.api;

/**
 * UNKNOWN 支付单核实结果：渠道查实后迁移的计数。
 * 渠道仍无定论（PENDING/UNKNOWN 或查询故障）的单据保持待核实。
 */
public record PaymentUnknownResolutionResult(
        int resolvedSucceeded,
        int resolvedFailed,
        int stillUnknown
) {
}
