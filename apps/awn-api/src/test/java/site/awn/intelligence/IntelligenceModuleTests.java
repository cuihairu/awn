package site.awn.intelligence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import site.awn.intelligence.api.CreateSourceCommand;
import site.awn.intelligence.api.IntelligenceFacade;
import site.awn.intelligence.api.PricePointView;
import site.awn.intelligence.api.SourceView;
import site.awn.intelligence.api.CollectAllReport;
import site.awn.intelligence.domain.CollectReport;
import site.awn.intelligence.sandbox.SandboxIntelligenceAdapter;
import site.awn.support.AbstractPostgresIntegrationTest;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 06：情报来源适配器 + 原始观察 + 标准化 + 价格历史。
 * 验收口径：
 * - 采集即落原始观察，标准化 / 质量检查后进价格历史（每条可溯源）；
 * - 数据分类与可信度明确（kind + trustTier 展示）；
 * - 采集失败不影响交易（失败记录在报告里返回，不抛出）；
 * - 来源内同一外部事件引用去重。
 */
@SpringBootTest
class IntelligenceModuleTests extends AbstractPostgresIntegrationTest {

    @Autowired IntelligenceFacade intelligence;
    @Autowired SandboxIntelligenceAdapter sandbox;

    @Test
    void collectPersistsRawNormalizedAndTraceablePriceHistory() {
        registerSandboxSource();
        String productRef = "ref-prod-" + UUID.randomUUID().toString().substring(0, 8);
        sandbox.offer(sandbox.nextRef(), productRef, 88000, "CNY", "AVAILABLE", "{\"page\":\"sandbox\"}");

        CollectReport report = intelligence.collect(SandboxIntelligenceAdapter.SOURCE_NO);

        assertThat(report.failed()).as("正常采集不应失败").isFalse();
        assertThat(report.rawCount()).isEqualTo(1);
        assertThat(report.okCount()).isEqualTo(1);

        List<PricePointView> history = intelligence.priceHistoryOf(productRef.substring("ref-".length()));
        assertThat(history).hasSize(1);
        PricePointView point = history.get(0);
        // 每条价格可溯源：来源、更新时间、可信度
        assertThat(point.sourceNo()).isEqualTo(SandboxIntelligenceAdapter.SOURCE_NO);
        assertThat(point.sourceName()).isNotBlank();
        assertThat(point.kind()).isEqualTo("OFFICIAL_PUBLISHED");
        assertThat(point.trustTier()).isEqualTo(1);
        assertThat(point.observedAt()).isNotNull();
        assertThat(point.price()).isEqualTo(88000);
        assertThat(point.currency()).isEqualTo("CNY");
    }

    @Test
    void qualityChecksRejectBadObservations() {
        registerSandboxSource();
        String prefix = "prod-bad-" + UUID.randomUUID().toString().substring(0, 6);

        // 负价格
        sandbox.offer(sandbox.nextRef(), "ref-" + prefix + "-a", -100, "CNY", null, "{}");
        // 币种非法
        sandbox.offer(sandbox.nextRef(), "ref-" + prefix + "-b", 1000, "RMB!", null, "{}");
        // 观察时间明显超前
        sandbox.offerAt(Instant.now().plusSeconds(3600), sandbox.nextRef(), "ref-" + prefix + "-c",
                1000, "CNY", null, "{}");

        CollectReport report = intelligence.collect(SandboxIntelligenceAdapter.SOURCE_NO);
        assertThat(report.rawCount()).isEqualTo(3);
        assertThat(report.rejectedCount()).as("坏数据必须被质量检查拦截").isEqualTo(3);
        assertThat(intelligence.priceHistoryOf(prefix + "-a")).isEmpty();
        assertThat(intelligence.priceHistoryOf(prefix + "-b")).isEmpty();
        assertThat(intelligence.priceHistoryOf(prefix + "-c")).isEmpty();
    }

    @Test
    void adapterFailureIsRecordedNotPropagated() {
        registerSandboxSource();
        sandbox.breakNextFetch();
        try {
            CollectReport report = intelligence.collect(SandboxIntelligenceAdapter.SOURCE_NO);
            assertThat(report.failed()).as("适配器失败必须记录在报告中").isTrue();
            assertThat(report.error()).contains("simulated");
        } finally {
            sandbox.repair();
        }
        // 修复后恢复采集
        sandbox.offer(sandbox.nextRef(), "ref-prod-recover", 6600, "CNY", null, "{}");
        CollectReport ok = intelligence.collect(SandboxIntelligenceAdapter.SOURCE_NO);
        assertThat(ok.failed()).isFalse();
        assertThat(ok.okCount()).isEqualTo(1);
    }

    @Test
    void duplicateExternalRefsAreCollectedOnce() {
        registerSandboxSource();
        String ref = "dup-" + UUID.randomUUID();
        sandbox.offer(ref, "ref-prod-dup", 12000, "CNY", null, "{}");
        assertThat(intelligence.collect(SandboxIntelligenceAdapter.SOURCE_NO).okCount()).isEqualTo(1);

        sandbox.offer(ref, "ref-prod-dup", 12000, "CNY", null, "{}");
        CollectReport second = intelligence.collect(SandboxIntelligenceAdapter.SOURCE_NO);
        assertThat(second.okCount()).as("同一外部事件引用不得重复入库").isZero();
    }

    @Test
    void dataCategoriesAndTrustTiersAreRecorded() {
        SourceView official = intelligence.registerSource(
                new CreateSourceCommand(null, "官方渠道", "OFFICIAL_PUBLISHED", 1));
        assertThat(official.kind()).isEqualTo("OFFICIAL_PUBLISHED");
        assertThat(official.trustTier()).isEqualTo(1);

        SourceView scraped = intelligence.registerSource(
                new CreateSourceCommand(null, "第三方采集", "THIRD_PARTY_LISTED", 3));
        assertThat(scraped.trustTier()).isEqualTo(3);

        // 可信度只允许 1..3
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        intelligence.registerSource(new CreateSourceCommand(null, "非法", "THIRD_PARTY_LISTED", 9)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void collectAllCoversEverySourceAndIsolatesFailures() {
        registerSandboxSource();
        SourceView noAdapter = intelligence.registerSource(
                new CreateSourceCommand(null, "无适配器来源", "THIRD_PARTY_LISTED", 3));
        String productRef = "ref-prod-all-" + UUID.randomUUID().toString().substring(0, 8);
        sandbox.offer(sandbox.nextRef(), productRef, 7700, "CNY", "AVAILABLE", "{}");

        CollectAllReport report = intelligence.collectAll();

        // 每个启用来源都被轮到：沙箱来源成功采集
        CollectReport sandboxReport = report.reports().stream()
                .filter(r -> r.sourceNo().equals(SandboxIntelligenceAdapter.SOURCE_NO))
                .findFirst().orElseThrow();
        assertThat(sandboxReport.failed()).isFalse();
        assertThat(sandboxReport.okCount()).isEqualTo(1);

        // 无适配器来源报告失败，但不中断整轮
        CollectReport noAdapterReport = report.reports().stream()
                .filter(r -> r.sourceNo().equals(noAdapter.sourceNo()))
                .findFirst().orElseThrow();
        assertThat(noAdapterReport.failed()).as("无适配器来源必须报告失败").isTrue();
        assertThat(report.failedCount()).as("失败来源必须计数").isGreaterThanOrEqualTo(1);

        assertThat(intelligence.priceHistoryOf(productRef.substring("ref-".length()))).hasSize(1);
    }

    @Test
    void collectAllSkipsDisabledSourcesButManualCollectStillWorks() {
        registerSandboxSource();
        SourceView disabled = intelligence.disableSource(SandboxIntelligenceAdapter.SOURCE_NO);
        assertThat(disabled.enabled()).isFalse();

        String productRef = "ref-prod-dis-" + UUID.randomUUID().toString().substring(0, 8);
        sandbox.offer(sandbox.nextRef(), productRef, 5500, "CNY", null, "{}");

        CollectAllReport report = intelligence.collectAll();
        assertThat(report.reports())
                .as("停用来源不参与调度采集")
                .noneMatch(r -> r.sourceNo().equals(SandboxIntelligenceAdapter.SOURCE_NO));

        // 手动单源采集不受停用限制（运营显式要求时仍可采集）
        CollectReport manual = intelligence.collect(SandboxIntelligenceAdapter.SOURCE_NO);
        assertThat(manual.failed()).isFalse();
        assertThat(manual.okCount()).isEqualTo(1);
        assertThat(intelligence.priceHistoryOf(productRef.substring("ref-".length()))).hasSize(1);
    }

    // ------------------------------------------------------------------

    private void registerSandboxSource() {
        try {
            intelligence.sourceOf(SandboxIntelligenceAdapter.SOURCE_NO);
        } catch (Exception e) {
            intelligence.registerSource(new CreateSourceCommand(
                    SandboxIntelligenceAdapter.SOURCE_NO, "沙箱渠道", "OFFICIAL_PUBLISHED", 1));
        }
    }
}
