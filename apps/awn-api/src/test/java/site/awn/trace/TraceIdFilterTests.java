package site.awn.trace;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import site.awn.shared.trace.TraceId;
import site.awn.support.AbstractPostgresIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 01：一次请求贯穿同一 traceId——过滤器接收/生成、响应回显、请求体内可见。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class TraceIdFilterTests extends AbstractPostgresIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void upstreamRequestIdIsHonoredAndEchoed() throws Exception {
        mockMvc.perform(get("/internal/trace").header("X-Request-Id", "req-abc-123"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", "req-abc-123"))
                .andExpect(jsonPath("$.traceId").value("req-abc-123"));
    }

    @Test
    void missingHeaderGeneratesTraceId() throws Exception {
        String body = mockMvc.perform(get("/internal/trace"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", org.hamcrest.Matchers.not("")))
                .andReturn().getResponse().getHeader("X-Request-Id");
        assertThat(body).isNotBlank().hasSize(32);
    }

    @Test
    void maliciousHeaderIsRejectedAndRegenerated() throws Exception {
        String echoed = mockMvc.perform(get("/internal/trace")
                        .header("X-Request-Id", "bad\r\nSet-Cookie: x=1"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("X-Request-Id");
        assertThat(echoed).as("非法头必须被丢弃并重新生成").doesNotContain("\r").doesNotContain("\n")
                .hasSize(32);
    }

    @TestConfiguration
    static class ProbeConfig {

        @Bean
        TraceProbeController traceProbeController() {
            return new TraceProbeController();
        }
    }

    @RestController
    static class TraceProbeController {

        @GetMapping("/internal/trace")
        public java.util.Map<String, String> trace() {
            return java.util.Map.of("traceId", TraceId.current() == null ? "" : TraceId.current());
        }
    }
}
