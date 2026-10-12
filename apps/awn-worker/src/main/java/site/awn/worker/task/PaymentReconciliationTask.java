package site.awn.worker.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import site.awn.payment.api.PaymentFacade;
import site.awn.payment.api.PaymentReconciliationReport;
import site.awn.shared.trace.Traces;

/**
 * 支付 / 退款对账任务：未终态单据 ↔ 渠道查询逐单核对。
 * 发现差异记录告警日志，不自动修复（修复是人工或升级流程的决策）。
 * 任务只读，重复执行幂等。
 */
@Component
public class PaymentReconciliationTask {

    private static final Logger log = LoggerFactory.getLogger(PaymentReconciliationTask.class);

    private final PaymentFacade payments;
    private final boolean workerEnabled;
    private final boolean taskEnabled;

    PaymentReconciliationTask(PaymentFacade payments,
                              @Value("${awn.worker.enabled:false}") boolean workerEnabled,
                              @Value("${awn.worker.tasks.payment-reconciliation:false}") boolean taskEnabled) {
        this.payments = payments;
        this.workerEnabled = workerEnabled;
        this.taskEnabled = taskEnabled;
    }

    @Scheduled(fixedDelayString = "${awn.worker.intervals.payment-reconciliation-ms:300000}")
    public void run() {
        if (!workerEnabled || !taskEnabled) {
            return;
        }
        Traces.withNewTrace(() -> {
            PaymentReconciliationReport report = payments.reconcile();
            if (!report.consistent()) {
                log.error("payment reconciliation differences={} paymentsChecked={} refundsChecked={}",
                        report.differences().size(), report.paymentsChecked(), report.refundsChecked());
                for (PaymentReconciliationReport.ReconciliationDifference d : report.differences()) {
                    log.error("reconciliation difference kind={} reference={} local={} channel={}",
                            d.kind(), d.reference(), d.localStatus(), d.channelStatus());
                }
            }
        });
    }
}
