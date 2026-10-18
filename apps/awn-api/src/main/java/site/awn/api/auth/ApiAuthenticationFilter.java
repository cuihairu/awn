package site.awn.api.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Requires a verified bearer token on endpoints that read or mutate buyer/seller data. */
@Component
public class ApiAuthenticationFilter extends OncePerRequestFilter {

    static final String USER_ID_ATTRIBUTE = ApiAuthenticationFilter.class.getName() + ".userId";

    private final ApiTokenService tokens;

    public ApiAuthenticationFilter(ApiTokenService tokens) {
        this.tokens = tokens;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path.startsWith("/api/v1/admin/") || path.startsWith("/api/v1/auth/")) return true;
        if ("POST".equals(request.getMethod()) && "/api/v1/checkout/quote".equals(path)) return true;
        if ("GET".equals(request.getMethod()) && path.equals("/api/v1/payments/channels")) return true;
        if (path.startsWith("/api/v1/orders/") || path.equals("/api/v1/orders")
                || path.startsWith("/api/v1/waitlist")
                || path.startsWith("/api/v1/disputes/")
                || path.startsWith("/api/v1/sellers/")
                || path.startsWith("/api/v1/listings/")
                || (path.startsWith("/api/v1/payments/") && path.endsWith("/retry"))) return false;
        return true;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            unauthorized(response);
            return;
        }
        try {
            long userId = tokens.verify(authorization.substring("Bearer ".length()).trim());
            request.setAttribute(USER_ID_ATTRIBUTE, userId);
            chain.doFilter(request, response);
        } catch (InvalidApiTokenException ex) {
            unauthorized(response);
        }
    }

    private static void unauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8");
        response.getWriter().write(
                "{\"code\":\"UNAUTHORIZED\",\"message\":\"valid bearer token required\"}");
    }
}
