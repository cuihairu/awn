package site.awn.worker.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import site.awn.payment.api.PaymentFacade;
import site.awn.payment.api.PaymentUnknownResolutionResult;
import site.awn.shared.trace.Traces;

/**
 * UNKNOWN 支付单核实任务：逐单向渠道查询，查实后迁移状态；
 * 查实成功但订单已终态 → 异常单自动退款（PaymentFacade 内闭环）。
 * 幂等：守卫条件更新裁决，重复执行无副作用。
 */
@Component
public class PaymentUnknownResolutionTask {

    private static final Logger log = LoggerFactory.getLogger(PaymentUnknownResolutionTask.class);

    private final PaymentFacade payments;
    private final boolean workerEnabled;
    private final boolean taskEnabled;

    PaymentUnknownResolutionTask(PaymentFacade payments,
                                 @Value("${awn.worker.enabled:false}") boolean workerEnabled,
                                 @Value("${awn.worker.tasks.payment-unknown-resolution:false}") boolean taskEnabled) {
        this.payments = payments;
        this.workerEnabled = workerEnabled;
        this.taskEnabled = taskEnabled;
    }

    @Scheduled(fixedDelayString = "${awn.worker.intervals.payment-unknown-resolution-ms:120000}")
    public void run() {
        if (!workerEnabled || !taskEnabled) {
            return;
        }
        Traces.withNewTrace(() -> {
            PaymentUnknownResolutionResult result = payments.resolveUnknownPayments();
            if (result.resolvedSucceeded() > 0 || result.resolvedFailed() > 0) {
                log.error("payment unknown resolution succeeded={} failed={} stillUnknown={}",
                        result.resolvedSucceeded(), result.resolvedFailed(), result.stillUnknown());
            }
        });
    }
}
