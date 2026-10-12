package site.awn.shared.error;

import org.slf4j.MDC;

/**
 * 请求追踪 ID 的读取入口。写入由请求过滤器负责（Phase 01 结构化日志与请求追踪），
 * 此处只读，保证错误信封在过滤器落地前也能安全返回 null。
 */
public final class TraceId {

    public static final String MDC_KEY = "traceId";

    private TraceId() {
    }

    public static String current() {
        return MDC.get(MDC_KEY);
    }
}
