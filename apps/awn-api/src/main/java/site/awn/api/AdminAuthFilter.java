package site.awn.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import site.awn.shared.ratelimit.RateLimiter;

import java.io.IOException;
import java.time.Duration;

/**
 * 运营接口鉴权：/api/v1/admin/** 与专场管理写操作（POST /api/v1/campaigns**）
 * 要求 X-Admin-Token 与配置一致，否则 401。
 * 契约（docs/api/contracts.md）：管理操作在 admin 之下，专场公开的只有查询与渲染 GET。
 *
 * <p>鉴权通过后按资源组（/api/v1/{resource}）限流，超限返回 429。
 * 额度默认 100 次/60s（awn.ratelimit.admin-limit / admin-window）。
 * 限流存储默认进程内（InMemoryRateLimiter），生产经 {@code AWN_RATELIMIT_STORE=redis}
 * 切到 Redis 固定窗口（多实例共享额度）。限流组件故障时 fail-open：限流是保护措施，
 * 不应让管理面整体不可用。
 */
@Component
public class AdminAuthFilter extends OncePerRequestFilter {

    static final String ADMIN_HEADER = "X-Admin-Token";

    private static final Logger log = LoggerFactory.getLogger(AdminAuthFilter.class);

    private final String adminToken;
    private final RateLimiter rateLimiter;
    private final int adminRateLimit;
    private final Duration adminRateWindow;

    public AdminAuthFilter(@Value("${awn.admin.token}") String adminToken,
                           RateLimiter rateLimiter,
                           @Value("${awn.ratelimit.admin-limit:100}") int adminRateLimit,
                           @Value("${awn.ratelimit.admin-window:60s}") Duration adminRateWindow) {
        this.adminToken = adminToken;
        this.rateLimiter = rateLimiter;
        this.adminRateLimit = adminRateLimit;
        this.adminRateWindow = adminRateWindow;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri.startsWith("/api/v1/admin")) {
            return false;
        }
        // 专场管理写操作与运营接口同权；公开读（GET /api/v1/campaigns**）放行
        return !(uri.startsWith("/api/v1/campaigns") && !"GET".equals(request.getMethod()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (adminToken.isBlank() || !adminToken.equals(request.getHeader(ADMIN_HEADER))) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":\"UNAUTHORIZED\",\"message\":\"admin token required\"}");
            return;
        }
        if (!tryAcquire(request)) {
            // Servlet 6.0 API 无 SC_TOO_MANY_REQUESTS 常量，429 为固定语义
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(
                    "{\"code\":\"RATE_LIMITED\",\"message\":\"rate limit exceeded, retry later\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    /** 按资源组取额度；限流组件故障时 fail-open 放行 */
    private boolean tryAcquire(HttpServletRequest request) {
        String key = request.getMethod() + ":" + resourceOf(request.getRequestURI());
        try {
            return rateLimiter.allow(key, adminRateLimit, adminRateWindow);
        } catch (RuntimeException ex) {
            log.warn("rate limiter unavailable, fail open for {}: {}", key, ex.getMessage());
            return true;
        }
    }

    /** 取 URI 第三段（/api/v1/{resource}/...）作为资源组，避免路径变量把额度打散 */
    private static String resourceOf(String uri) {
        String[] segments = uri.split("/");
        return segments.length > 3 ? segments[3] : uri;
    }
}
