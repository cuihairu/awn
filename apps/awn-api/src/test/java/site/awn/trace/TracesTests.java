package site.awn.trace;

import org.junit.jupiter.api.Test;
import site.awn.shared.trace.TraceId;
import site.awn.shared.trace.Traces;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 01：traceId 跨线程传递。线程池切换丢 MDC，必须经 wrap 显式携带。
 */
class TracesTests {

    @Test
    void withNewTraceSetsAndClearsTraceId() {
        assertThat(TraceId.current()).isNull();
        String[] seen = new String[1];
        Traces.withNewTrace(() -> seen[0] = TraceId.current());
        assertThat(seen[0]).as("任务体内可见新 traceId").isNotBlank().hasSize(32);
        assertThat(TraceId.current()).as("结束后不污染调用线程").isNull();
    }

    @Test
    void withNewTraceRestoresPreviousValue() {
        TraceId.set("outer-trace");
        try {
            Traces.withNewTrace(() -> assertThat(TraceId.current()).isNotEqualTo("outer-trace"));
            assertThat(TraceId.current()).isEqualTo("outer-trace");
        } finally {
            TraceId.clear();
        }
    }

    @Test
    void wrapCarriesTraceIdAcrossThreads() throws Exception {
        TraceId.set("cross-thread-trace");
        try {
            ExecutorService executor = Executors.newSingleThreadExecutor();
            String seen = executor.submit(Traces.wrap(() -> TraceId.current().length() > 0))
                    .get() ? "present" : "missing";
            executor.shutdown();
            assertThat(seen).isEqualTo("present");
        } finally {
            TraceId.clear();
        }
    }
}
