package site.awn.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 运营接口鉴权：/api/v1/admin/** 与专场管理写操作（POST /api/v1/campaigns**）
 * 要求 X-Admin-Token 与配置一致，否则 401。
 * 契约（docs/api/contracts.md）：管理操作在 admin 之下，专场公开的只有查询与渲染 GET。
 */
@Component
public class AdminAuthFilter extends OncePerRequestFilter {

    static final String ADMIN_HEADER = "X-Admin-Token";

    private final String adminToken;

    public AdminAuthFilter(@Value("${awn.admin.token}") String adminToken) {
        this.adminToken = adminToken;
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
        chain.doFilter(request, response);
    }
}
