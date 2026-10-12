package site.awn.payment.api;

import java.util.List;

/**
 * 支付/退款对账报告：本地未终态单据 ↔ 渠道查询结果逐单核对。
 * differences 为空表示账实相符；发现差异只报告不修复。
 */
public record PaymentReconciliationReport(
        int paymentsChecked,
        int refundsChecked,
        List<ReconciliationDifference> differences
) {

    public PaymentReconciliationReport {
        differences = differences == null ? List.of() : List.copyOf(differences);
    }

    public boolean consistent() {
        return differences.isEmpty();
    }

    public record ReconciliationDifference(
            String kind,
            String reference,
            String localStatus,
            String channelStatus
    ) {
    }
}
