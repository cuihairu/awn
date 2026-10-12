package site.awn.worker.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import site.awn.shared.outbox.OutboxDispatchService;
import site.awn.shared.trace.Traces;

import java.time.Instant;

/**
 * Outbox 分发任务：到期事件投递给各模块消费者（Inbox 去重）。
 * 进程重启不丢事件（PENDING 持久化）；投递失败按退避重试，超上限转 FAILED。
 */
@Component
public class OutboxDispatchTask {

    private static final Logger log = LoggerFactory.getLogger(OutboxDispatchTask.class);

    private final OutboxDispatchService dispatcher;
    private final boolean workerEnabled;
    private final boolean taskEnabled;

    OutboxDispatchTask(OutboxDispatchService dispatcher,
                       @Value("${awn.worker.enabled:false}") boolean workerEnabled,
                       @Value("${awn.worker.tasks.outbox-dispatch:false}") boolean taskEnabled) {
        this.dispatcher = dispatcher;
        this.workerEnabled = workerEnabled;
        this.taskEnabled = taskEnabled;
    }

    @Scheduled(fixedDelayString = "${awn.worker.intervals.outbox-dispatch-ms:5000}")
    public void run() {
        if (!workerEnabled || !taskEnabled) {
            return;
        }
        Traces.withNewTrace(() -> {
            int delivered = dispatcher.dispatchDue(Instant.now());
            if (delivered > 0) {
                log.info("outbox dispatched messages={}", delivered);
            }
        });
    }
}
