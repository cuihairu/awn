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
 * 运营接口鉴权：/api/v1/admin/** 要求 X-Admin-Token 与配置一致，否则 401。
 * 契约（docs/api/contracts.md）：库存调整等运营接口要求权限校验。
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
        return !request.getRequestURI().startsWith("/api/v1/admin");
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
