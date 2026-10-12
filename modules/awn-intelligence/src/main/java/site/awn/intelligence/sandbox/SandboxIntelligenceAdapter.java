package site.awn.intelligence.sandbox;

import org.springframework.stereotype.Component;
import site.awn.intelligence.domain.IntelligenceAdapter;
import site.awn.intelligence.domain.ObservationDraft;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 沙箱情报适配器（测试适配器）：测试用例向队列投放观察草稿，
 * 采集轮次消费之。也可模拟适配器抛错（投放失败标记）。
 */
@Component
public class SandboxIntelligenceAdapter implements IntelligenceAdapter {

    public static final String SOURCE_NO = "SRC-SANDBOX";

    private final Deque<ObservationDraft> queue = new ConcurrentLinkedDeque<>();
    private volatile boolean broken = false;

    @Override
    public String sourceNo() {
        return SOURCE_NO;
    }

    @Override
    public List<ObservationDraft> fetch() {
        if (broken) {
            throw new IllegalStateException("sandbox adapter failure (simulated)");
        }
        List<ObservationDraft> drained = new ArrayList<>();
        ObservationDraft draft;
        while ((draft = queue.poll()) != null) {
            drained.add(draft);
        }
        return drained;
    }

    /** 测试侧投放一条观察草稿 */
    public SandboxIntelligenceAdapter offer(String externalRef, String productRef, long price,
                                            String currency, String availability, String payload) {
        queue.add(new ObservationDraft(externalRef, productRef,
                java.time.Instant.now(), price, currency, availability, payload));
        return this;
    }

    /** 测试侧投放一条指定观察时间的草稿 */
    public SandboxIntelligenceAdapter offerAt(java.time.Instant observedAt, String externalRef,
                                              String productRef, long price, String currency,
                                              String availability, String payload) {
        queue.add(new ObservationDraft(externalRef, productRef, observedAt, price, currency, availability, payload));
        return this;
    }

    /** 模拟适配器故障（下一轮采集抛错） */
    public void breakNextFetch() {
        this.broken = true;
    }

    public void repair() {
        this.broken = false;
    }

    public String nextRef() {
        return "ref-" + UUID.randomUUID().toString().substring(0, 12);
    }
}
