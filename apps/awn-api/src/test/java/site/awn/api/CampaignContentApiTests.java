package site.awn.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import site.awn.support.AbstractPostgresIntegrationTest;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 专场内容 HTTP 契约（docs/api/contracts.md「专场列表与内容接口」）。
 *
 * <p>写操作与专场管理同权（X-Admin-Token），公开读（列表 / 须知 / 常见问题 / 素材）免 token：
 * 三套实例要发得出去，前端不能手动输专场码。
 *
 * <p>模块层语义（排序、归属专场删除、id 可解析）由 CampaignModuleTests 覆盖。
 */
@SpringBootTest
@AutoConfigureMockMvc
class CampaignContentApiTests extends AbstractPostgresIntegrationTest {

    private static final String TOKEN_HEADER = "X-Admin-Token";
    private static final String TOKEN = "awn-admin-dev-token";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;

    private String uid() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    /** 写入响应的条目 id（引用型 props 按此 id 匹配） */
    private long idOf(String json) {
        try {
            return objectMapper.readTree(json).get("id").asLong();
        } catch (Exception e) {
            throw new IllegalStateException("no id in response: " + json, e);
        }
    }

    /** 建专场 → 写配置 → 校验 → 发布，内容端点的可见性前提 */
    private String publishedCampaign() throws Exception {
        String code = "camp-api-" + uid();
        mvc.perform(post("/api/v1/campaigns")
                        .header(TOKEN_HEADER, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"campaignCode":"%s","title":"内容契约专场","subtitle":"s"}
                                """.formatted(code)))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/campaigns/" + code + "/draft")
                        .header(TOKEN_HEADER, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"config":{"schemaVersion":1,"template":"concert","sections":[
                                  {"id":"rules","type":"rulePanel","props":{"rules":["凭票入场"]}},
                                  {"id":"faq","type":"faq","props":{"entries":[
                                    {"question":"如何取票？","answer":"开演前 1 小时"}]}}]}}
                                """))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/campaigns/" + code + "/validate").header(TOKEN_HEADER, TOKEN))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/campaigns/" + code + "/publish").header(TOKEN_HEADER, TOKEN))
                .andExpect(status().isOk());
        return code;
    }

    @Test
    void contentWritesRequireAdminTokenWhileReadsStayPublic() throws Exception {
        String code = publishedCampaign();

        mvc.perform(post("/api/v1/campaigns/" + code + "/rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"入场须知","content":"凭电子票入场"}
                                """))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/v1/campaigns/" + code + "/rules/1"))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/api/v1/campaigns/" + code + "/rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void contentEndpointsCreateReadAndDeleteEntries() throws Exception {
        String code = publishedCampaign();

        String ruleId = mvc.perform(post("/api/v1/campaigns/" + code + "/rules")
                        .header(TOKEN_HEADER, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"退票须知","content":"开演前 48 小时可退","displayOrder":1}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("/rules/")))
                .andExpect(jsonPath("$.title").value("退票须知"))
                .andReturn().getResponse().getContentAsString();

        String faqId = mvc.perform(post("/api/v1/campaigns/" + code + "/faq")
                        .header(TOKEN_HEADER, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"如何取票？","answer":"开演前 1 小时取票"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.question").value("如何取票？"))
                .andReturn().getResponse().getContentAsString();

        String assetId = mvc.perform(post("/api/v1/campaigns/" + code + "/assets")
                        .header(TOKEN_HEADER, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"assetType":"IMAGE","url":"https://cdn.awn.site/live/stage.jpg",
                                 "altText":"舞台现场","displayOrder":0}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.assetType").value("IMAGE"))
                .andReturn().getResponse().getContentAsString();

        mvc.perform(get("/api/v1/campaigns/" + code + "/rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(idOf(ruleId)))
                .andExpect(jsonPath("$[0].content").value("开演前 48 小时可退"));
        mvc.perform(get("/api/v1/campaigns/" + code + "/faq"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(idOf(faqId)))
                .andExpect(jsonPath("$[0].answer").value("开演前 1 小时取票"));
        mvc.perform(get("/api/v1/campaigns/" + code + "/assets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(idOf(assetId)))
                .andExpect(jsonPath("$[0].url").value("https://cdn.awn.site/live/stage.jpg"))
                .andExpect(jsonPath("$[0].altText").value("舞台现场"));

        mvc.perform(delete("/api/v1/campaigns/" + code + "/rules/" + idOf(ruleId)).header(TOKEN_HEADER, TOKEN))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/v1/campaigns/" + code + "/faq/" + idOf(faqId)).header(TOKEN_HEADER, TOKEN))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/v1/campaigns/" + code + "/assets/" + idOf(assetId)).header(TOKEN_HEADER, TOKEN))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/campaigns/" + code + "/rules"))
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/v1/campaigns/" + code + "/faq"))
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/v1/campaigns/" + code + "/assets"))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void publicCampaignListShowsPublishedCampaignsOnly() throws Exception {
        String code = publishedCampaign();
        String draft = "camp-draft-" + uid();
        mvc.perform(post("/api/v1/campaigns")
                        .header(TOKEN_HEADER, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"campaignCode":"%s","title":"草稿专场","subtitle":null}
                                """.formatted(draft)))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/campaigns"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].campaignCode", org.hamcrest.Matchers.hasItem(code)))
                .andExpect(jsonPath("$[*].campaignCode", org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.hasItem(draft))))
                .andExpect(jsonPath("$[*].status", org.hamcrest.Matchers.everyItem(
                        org.hamcrest.Matchers.equalTo("PUBLISHED"))));
    }

    @Test
    void unknownCampaignAndBadAssetTypeAreRejected() throws Exception {
        String missing = "camp-missing-" + uid();

        mvc.perform(get("/api/v1/campaigns/" + missing + "/rules"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CAMPAIGN_NOT_FOUND"));
        mvc.perform(get("/api/v1/campaigns/" + missing + "/faq"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/campaigns/" + missing + "/assets"))
                .andExpect(status().isNotFound());

        mvc.perform(post("/api/v1/campaigns/" + missing + "/faq")
                        .header(TOKEN_HEADER, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"问？","answer":"答"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CAMPAIGN_NOT_FOUND"));

        mvc.perform(post("/api/v1/campaigns/" + publishedCampaign() + "/assets")
                        .header(TOKEN_HEADER, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"assetType":"HOLOGRAM","url":"https://cdn.awn.site/x.jpg"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void blankContentFieldsAreRejected() throws Exception {
        String code = publishedCampaign();

        // 空正文不构成内容：公开面不出现空条目
        mvc.perform(post("/api/v1/campaigns/" + code + "/rules")
                        .header(TOKEN_HEADER, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"须知","content":"   "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(post("/api/v1/campaigns/" + code + "/faq")
                        .header(TOKEN_HEADER, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"如何取票？","answer":"  "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mvc.perform(get("/api/v1/campaigns/" + code + "/rules"))
                .andExpect(jsonPath("$.length()").value(0));
    }
}