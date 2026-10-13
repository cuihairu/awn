package site.awn.worker.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import site.awn.intelligence.api.CollectAllReport;
import site.awn.intelligence.api.IntelligenceFacade;
import site.awn.shared.trace.Traces;

/**
 * 情报采集调度任务：按配置周期对全部启用来源执行一轮全量采集。
 * 单源失败不中断整轮（CollectReport.error 携带失败原因）；手动单源采集接口不受影响。
 */
@Component
public class IntelligenceCollectionTask {

    private static final Logger log = LoggerFactory.getLogger(IntelligenceCollectionTask.class);

    private final IntelligenceFacade intelligence;
    private final boolean workerEnabled;
    private final boolean taskEnabled;

    IntelligenceCollectionTask(IntelligenceFacade intelligence,
                               @Value("${awn.worker.enabled:false}") boolean workerEnabled,
                               @Value("${awn.worker.tasks.intelligence-collection:false}") boolean taskEnabled) {
        this.intelligence = intelligence;
        this.workerEnabled = workerEnabled;
        this.taskEnabled = taskEnabled;
    }

    @Scheduled(fixedDelayString = "${awn.worker.intervals.intelligence-collection-ms:1800000}")
    public void run() {
        if (!workerEnabled || !taskEnabled) {
            return;
        }
        Traces.withNewTrace(() -> {
            CollectAllReport report = intelligence.collectAll();
            if (report.failedCount() == 0) {
                log.info("intelligence collection ok, sources={}", report.checked());
            } else {
                log.warn("intelligence collection finished, sources={} failed={}",
                        report.checked(), report.failedCount());
            }
        });
    }
}
