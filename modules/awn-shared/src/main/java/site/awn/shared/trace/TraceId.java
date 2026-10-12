package site.awn.shared.trace;

import org.slf4j.MDC;

/**
 * 请求追踪 ID 的 MDC 入口：写入由 API 过滤器与 Worker 任务包装器负责，
 * 读取贯穿错误信封与日志 pattern（%X{traceId}）。
 */
public final class TraceId {

    public static final String MDC_KEY = "traceId";

    private TraceId() {
    }

    public static String current() {
        return MDC.get(MDC_KEY);
    }

    public static void set(String traceId) {
        MDC.put(MDC_KEY, traceId);
    }

    public static void clear() {
        MDC.remove(MDC_KEY);
    }
}
