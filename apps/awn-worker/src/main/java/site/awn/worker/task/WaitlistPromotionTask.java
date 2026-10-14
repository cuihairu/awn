package site.awn.worker.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import site.awn.order.api.WaitlistFacade;
import site.awn.order.api.WaitlistPromotionResult;
import site.awn.shared.trace.Traces;

import java.time.Instant;

/**
 * 候补递补任务（契约「排队 / 候补」）：按池 FIFO 为队首原子预留并生成待支付订单，
 * 同时把已递补席位与订单状态对账（已支付成单、已超时/取消过期回池）。
 * 幂等由席位状态机守卫保证：同一候补编号的建单幂等键重放返回同一订单，
 * 重复执行不重复建单、不重复占位。
 */
@Component
public class WaitlistPromotionTask {

    private static final Logger log = LoggerFactory.getLogger(WaitlistPromotionTask.class);

    private final WaitlistFacade waitlist;
    private final boolean workerEnabled;
    private final boolean taskEnabled;

    WaitlistPromotionTask(WaitlistFacade waitlist,
                          @Value("${awn.worker.enabled:false}") boolean workerEnabled,
                          @Value("${awn.worker.tasks.waitlist-promotion:false}") boolean taskEnabled) {
        this.waitlist = waitlist;
        this.workerEnabled = workerEnabled;
        this.taskEnabled = taskEnabled;
    }

    @Scheduled(fixedDelayString = "${awn.worker.intervals.waitlist-promotion-ms:60000}")
    public void run() {
        if (!workerEnabled || !taskEnabled) {
            return;
        }
        Traces.withNewTrace(() -> {
            WaitlistPromotionResult result = waitlist.promoteAvailable(Instant.now());
            if (!result.failed().isEmpty()) {
                log.error("waitlist promotion finished, promoted={} claimed={} expired={} failed={}",
                        result.promoted(), result.claimed(), result.expired(), result.failed());
            } else if (result.promoted() > 0 || result.claimed() > 0 || result.expired() > 0) {
                log.info("waitlist promotion ok, promoted={} claimed={} expired={}",
                        result.promoted(), result.claimed(), result.expired());
            }
        });
    }
}
