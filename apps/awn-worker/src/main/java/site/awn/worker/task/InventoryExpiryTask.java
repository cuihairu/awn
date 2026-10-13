package site.awn.worker.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import site.awn.inventory.api.InventoryFacade;
import site.awn.inventory.api.ReservationExpiryResult;
import site.awn.shared.trace.Traces;

import java.time.Instant;

/**
 * 预留到期扫描任务：释放已过期的 ACTIVE 预留并回补库存。
 * 幂等由预留状态机守卫保证，重复执行不重复回补。
 */
@Component
public class InventoryExpiryTask {

    private static final Logger log = LoggerFactory.getLogger(InventoryExpiryTask.class);

    private final InventoryFacade inventory;
    private final boolean workerEnabled;
    private final boolean taskEnabled;

    InventoryExpiryTask(InventoryFacade inventory,
                        @Value("${awn.worker.enabled:false}") boolean workerEnabled,
                        @Value("${awn.worker.tasks.inventory-expiry:false}") boolean taskEnabled) {
        this.inventory = inventory;
        this.workerEnabled = workerEnabled;
        this.taskEnabled = taskEnabled;
    }

    @Scheduled(fixedDelayString = "${awn.worker.intervals.inventory-expiry-ms:60000}")
    public void run() {
        if (!workerEnabled || !taskEnabled) {
            return;
        }
        Traces.withNewTrace(() -> {
            ReservationExpiryResult result = inventory.releaseExpiredReservations(Instant.now());
            if (result.released() > 0) {
                log.info("inventory expiry released={}", result.released());
            }
            if (!result.failed().isEmpty()) {
                log.error("inventory expiry failed reservations={}", result.failed());
            }
        });
    }
}
