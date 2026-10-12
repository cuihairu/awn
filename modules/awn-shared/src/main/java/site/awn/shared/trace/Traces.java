package site.awn.shared.trace;

import java.util.UUID;
import java.util.concurrent.Callable;

/**
 * Worker 任务与异步执行的 traceId 传递：
 * 线程池切换会丢 MDC，跨线程必须经 {@link #wrap} 显式携带。
 */
public final class Traces {

    private Traces() {
    }

    /** 新 traceId（32 位无连字符，与 HTTP 过滤器同构） */
    public static String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /** 在新 traceId 下执行任务体；结束清理，不污染线程池线程 */
    public static void withNewTrace(Runnable task) {
        String previous = TraceId.current();
        TraceId.set(newTraceId());
        try {
            task.run();
        } finally {
            restore(previous);
        }
    }

    /** 携带当前线程 MDC 到目标线程（提交时快照，执行时恢复并清理） */
    public static Runnable wrap(Runnable task) {
        String snapshot = TraceId.current();
        return () -> {
            String previous = TraceId.current();
            TraceId.set(snapshot);
            try {
                task.run();
            } finally {
                restore(previous);
            }
        };
    }

    /** 同 {@link #wrap(Runnable)}，返回值版 */
    public static <V> Callable<V> wrap(Callable<V> task) {
        String snapshot = TraceId.current();
        return () -> {
            String previous = TraceId.current();
            TraceId.set(snapshot);
            try {
                return task.call();
            } finally {
                restore(previous);
            }
        };
    }

    private static void restore(String previous) {
        if (previous == null) {
            TraceId.clear();
        } else {
            TraceId.set(previous);
        }
    }
}
