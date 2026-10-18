package site.awn.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import site.awn.api.auth.ApiTokenService;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** Existing API integration fixtures use user 42 unless a test explicitly overrides the token. */
@TestConfiguration(proxyBeanMethods = false)
public class TestMockMvcAuthConfiguration {

    @Bean
    MockMvcBuilderCustomizer defaultBearerToken(ApiTokenService tokens) {
        String token = tokens.issue(42L);
        return builder -> builder.defaultRequest(get("/").header("Authorization", "Bearer " + token));
    }
}
