package site.awn.worker.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import site.awn.inventory.api.InventoryFacade;
import site.awn.inventory.api.ReconciliationReport;
import site.awn.shared.trace.Traces;

/**
 * 库存对账任务：预留记录 ↔ 余额计数器逐池核对。
 * 差异只报告不修复——自动改写计数器会掩盖真正的缺陷。
 */
@Component
public class InventoryReconciliationTask {

    private static final Logger log = LoggerFactory.getLogger(InventoryReconciliationTask.class);

    private final InventoryFacade inventory;
    private final boolean workerEnabled;
    private final boolean taskEnabled;

    InventoryReconciliationTask(InventoryFacade inventory,
                                @Value("${awn.worker.enabled:false}") boolean workerEnabled,
                                @Value("${awn.worker.tasks.inventory-reconciliation:false}") boolean taskEnabled) {
        this.inventory = inventory;
        this.workerEnabled = workerEnabled;
        this.taskEnabled = taskEnabled;
    }

    @Scheduled(fixedDelayString = "${awn.worker.intervals.inventory-reconciliation-ms:600000}")
    public void run() {
        if (!workerEnabled || !taskEnabled) {
            return;
        }
        Traces.withNewTrace(() -> {
            ReconciliationReport report = inventory.reconcile();
            if (report.consistent()) {
                log.info("inventory reconciliation ok, pools={}", report.poolsChecked());
            } else {
                log.error("inventory reconciliation violations={} pools={}",
                        report.violations(), report.poolsChecked());
            }
        });
    }
}
