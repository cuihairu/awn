package site.awn.worker.task;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import site.awn.intelligence.api.CreateSourceCommand;
import site.awn.intelligence.api.IntelligenceFacade;
import site.awn.intelligence.sandbox.SandboxIntelligenceAdapter;
import site.awn.support.AbstractPostgresIntegrationTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 情报采集调度任务包装测试（调度开关关闭，直接调 run()）。
 * 验收口径（todo 情报采集调度器）：开启时按配置周期执行全量采集；关闭时不动作。
 */
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "awn.worker.enabled=false"
})
class IntelligenceCollectionTaskTest extends AbstractPostgresIntegrationTest {

    @Autowired IntelligenceFacade intelligence;
    @Autowired SandboxIntelligenceAdapter sandbox;

    @Test
    void runCollectsAllSourcesWhenEnabled() {
        registerSandboxSource();
        String productRef = "ref-prod-task-" + UUID.randomUUID().toString().substring(0, 8);
        sandbox.offer(sandbox.nextRef(), productRef, 6600, "CNY", null, "{}");

        new IntelligenceCollectionTask(intelligence, true, true).run();

        assertThat(intelligence.priceHistoryOf(productRef.substring("ref-".length()))).hasSize(1);
    }

    @Test
    void runDoesNothingWhenDisabled() {
        registerSandboxSource();
        String productRef = "ref-prod-task-off-" + UUID.randomUUID().toString().substring(0, 8);
        sandbox.offer(sandbox.nextRef(), productRef, 6600, "CNY", null, "{}");

        new IntelligenceCollectionTask(intelligence, false, true).run();
        new IntelligenceCollectionTask(intelligence, true, false).run();

        assertThat(intelligence.priceHistoryOf(productRef.substring("ref-".length()))).isEmpty();
    }

    private void registerSandboxSource() {
        try {
            intelligence.sourceOf(SandboxIntelligenceAdapter.SOURCE_NO);
        } catch (Exception e) {
            intelligence.registerSource(new CreateSourceCommand(
                    SandboxIntelligenceAdapter.SOURCE_NO, "沙箱渠道", "OFFICIAL_PUBLISHED", 1));
        }
    }
}
