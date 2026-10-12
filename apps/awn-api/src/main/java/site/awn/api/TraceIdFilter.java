package site.awn.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import site.awn.shared.trace.Traces;
import site.awn.shared.trace.TraceId;

import java.io.IOException;

/**
 * 请求追踪过滤器：一次请求全链路（含错误信封与所有日志）共享同一 traceId。
 * 上游传入 X-Request-Id 则沿用（便于跨服务关联），否则生成；
 * 非法值一律丢弃并生成，防止日志注入（换行、控制字符）。
 */
@Component
public class TraceIdFilter extends OncePerRequestFilter {

    static final String HEADER = "X-Request-Id";

    private static final int MAX_LENGTH = 64;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = accept(request.getHeader(HEADER));
        if (traceId == null) {
            traceId = Traces.newTraceId();
        }
        TraceId.set(traceId);
        response.setHeader(HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            TraceId.clear();
        }
    }

    /** 只接受字母数字与 - _ . ，且不超过 64 位；否则返回 null 由调用方生成 */
    static String accept(String value) {
        if (value == null || value.isEmpty() || value.length() > MAX_LENGTH) {
            return null;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == '.')) {
                return null;
            }
        }
        return value;
    }
}
