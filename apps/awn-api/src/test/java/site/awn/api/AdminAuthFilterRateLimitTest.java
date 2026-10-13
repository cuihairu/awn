package site.awn.api;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import site.awn.shared.ratelimit.InMemoryRateLimiter;
import site.awn.shared.ratelimit.RateLimiter;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 管理面限流验收：鉴权通过后按资源组限流，超限 429；
 * 未过鉴权的请求不计入额度；限流组件故障 fail-open。
 */
class AdminAuthFilterRateLimitTest {

    private static final String TOKEN = "awn-admin-dev-token";

    @Test
    void overLimitRequestGets429AfterTokenCheckPasses() throws Exception {
        AdminAuthFilter filter = new AdminAuthFilter(TOKEN, new InMemoryRateLimiter(), 2, Duration.ofSeconds(60));
        jakarta.servlet.FilterChain chain = mock(jakarta.servlet.FilterChain.class);

        assertThat(perform(filter, chain, "/api/v1/campaigns").getStatus()).isEqualTo(200);
        assertThat(perform(filter, chain, "/api/v1/campaigns").getStatus()).isEqualTo(200);

        MockHttpServletResponse limited = perform(filter, chain, "/api/v1/campaigns");
        assertThat(limited.getStatus()).isEqualTo(429);
        assertThat(limited.getContentAsString()).contains("RATE_LIMITED");
        // 第三次被限流拦截，未到达 controller
        verify(chain, times(2)).doFilter(any(), any());
    }

    @Test
    void limitIsScopedByResourceGroup() throws Exception {
        AdminAuthFilter filter = new AdminAuthFilter(TOKEN, new InMemoryRateLimiter(), 1, Duration.ofSeconds(60));
        jakarta.servlet.FilterChain chain = mock(jakarta.servlet.FilterChain.class);

        assertThat(perform(filter, chain, "/api/v1/campaigns").getStatus()).isEqualTo(200);
        // 不同资源组（admin vs campaigns）额度独立
        assertThat(perform(filter, chain, "/api/v1/admin/alerts").getStatus()).isEqualTo(200);
        assertThat(perform(filter, chain, "/api/v1/campaigns").getStatus()).isEqualTo(429);
    }

    @Test
    void unauthenticatedRequestIsNotCounted() throws Exception {
        RateLimiter rateLimiter = mock(RateLimiter.class);
        AdminAuthFilter filter = new AdminAuthFilter(TOKEN, rateLimiter, 10, Duration.ofSeconds(60));
        jakarta.servlet.FilterChain chain = mock(jakarta.servlet.FilterChain.class);

        MockHttpServletResponse response = perform(filter, chain, "/api/v1/campaigns", false);
        assertThat(response.getStatus()).isEqualTo(401);
        // 鉴权失败即返回，不消费限流额度
        verify(rateLimiter, never()).allow(anyString(), anyInt(), any());
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void rateLimiterFailureFailsOpen() throws Exception {
        RateLimiter rateLimiter = mock(RateLimiter.class);
        when(rateLimiter.allow(anyString(), anyInt(), any()))
                .thenThrow(new IllegalStateException("redis down"));
        AdminAuthFilter filter = new AdminAuthFilter(TOKEN, rateLimiter, 1, Duration.ofSeconds(60));
        jakarta.servlet.FilterChain chain = mock(jakarta.servlet.FilterChain.class);

        assertThat(perform(filter, chain, "/api/v1/admin/alerts").getStatus()).isEqualTo(200);
        verify(chain).doFilter(any(), any());
    }

    private MockHttpServletResponse perform(AdminAuthFilter filter,
                                             jakarta.servlet.FilterChain chain,
                                             String uri) throws Exception {
        return perform(filter, chain, uri, true);
    }

    private MockHttpServletResponse perform(AdminAuthFilter filter,
                                             jakarta.servlet.FilterChain chain,
                                             String uri,
                                             boolean withToken) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        if (withToken) {
            request.addHeader(AdminAuthFilter.ADMIN_HEADER, TOKEN);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }
}
