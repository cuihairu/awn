package site.awn.worker.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import site.awn.order.api.OrderFacade;
import site.awn.order.api.OrderTimeoutResult;
import site.awn.shared.trace.Traces;

import java.time.Instant;

/**
 * 订单超时任务：支付截止期已过的待支付订单 → EXPIRED + 释放库存。
 * 幂等由订单状态机守卫保证，重复执行不重复释放。
 */
@Component
public class OrderTimeoutTask {

    private static final Logger log = LoggerFactory.getLogger(OrderTimeoutTask.class);

    private final OrderFacade orders;
    private final boolean workerEnabled;
    private final boolean taskEnabled;

    OrderTimeoutTask(OrderFacade orders,
                     @Value("${awn.worker.enabled:false}") boolean workerEnabled,
                     @Value("${awn.worker.tasks.order-timeout:false}") boolean taskEnabled) {
        this.orders = orders;
        this.workerEnabled = workerEnabled;
        this.taskEnabled = taskEnabled;
    }

    @Scheduled(fixedDelayString = "${awn.worker.intervals.order-timeout-ms:60000}")
    public void run() {
        if (!workerEnabled || !taskEnabled) {
            return;
        }
        Traces.withNewTrace(() -> {
            OrderTimeoutResult result = orders.releaseExpiredOrders(Instant.now());
            if (result.expired() > 0) {
                log.info("order timeout released={} skipped={}", result.expired(), result.skipped());
            }
            if (!result.failed().isEmpty()) {
                log.error("order timeout failed orders={}", result.failed());
            }
        });
    }
}
