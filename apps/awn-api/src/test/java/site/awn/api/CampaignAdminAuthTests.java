package site.awn.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import site.awn.support.AbstractPostgresIntegrationTest;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 06：专场管理写接口鉴权验收。
 * 契约（docs/api/contracts.md）规定专场管理操作在 admin 之下、公开的只有查询与渲染 GET；
 * 因此 POST /api/v1/campaigns** 必须与运营接口同权（X-Admin-Token）。
 */
@SpringBootTest
@AutoConfigureMockMvc
class CampaignAdminAuthTests extends AbstractPostgresIntegrationTest {

    private static final String TOKEN_HEADER = "X-Admin-Token";
    private static final String TOKEN = "awn-admin-dev-token";

    @Autowired MockMvc mockMvc;

    @Test
    void campaignCreateWithoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/campaigns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"campaignCode\":\"C-AUTH-1\",\"title\":\"鉴权\",\"subtitle\":\"t\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void campaignPublishWithWrongTokenIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/campaigns/C-AUTH-X/publish").header(TOKEN_HEADER, "wrong"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void campaignWriteWithTokenSucceedsAndReadStaysPublic() throws Exception {
        String code = "C-AUTH-" + UUID.randomUUID().toString().substring(0, 8);
        mockMvc.perform(post("/api/v1/campaigns")
                        .header(TOKEN_HEADER, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"campaignCode\":\"" + code + "\",\"title\":\"鉴权专场\",\"subtitle\":\"s\"}"))
                .andExpect(status().isOk());
        // 公开读不受影响：无 token 可读专场
        mockMvc.perform(get("/api/v1/campaigns/" + code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.campaignCode").value(code));
    }
}
