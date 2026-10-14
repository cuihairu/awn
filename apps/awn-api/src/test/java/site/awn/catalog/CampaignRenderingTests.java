package site.awn.catalog;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import site.awn.campaign.application.CampaignTemplateRenderer;
import site.awn.campaign.application.CampaignTemplates;
import site.awn.campaign.domain.CampaignConfigValidationException;
import site.awn.campaign.domain.CampaignConfigValidator;
import site.awn.support.AbstractPostgresIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 02：两种专场模板渲染 + 配置校验器单测。
 */
@SpringBootTest
class CampaignRenderingTests extends AbstractPostgresIntegrationTest {

    @Autowired CampaignTemplateRenderer renderer;
    @Autowired CampaignConfigValidator validator;
    @Autowired ObjectMapper objectMapper;

    @Test
    void motorsportTemplateRenders() throws Exception {
        var config = CampaignTemplates.motorsport("AWN F1 专场", "赛事票务动态",
                "asset_001", "event_001",
                java.util.List.of("凭电子票入场", "禁止携带外区食品"));

        validator.validate(config);

        var page = renderer.render(config);
        assertThat(page.template).isEqualTo("motorsport");
        assertThat(page.sections).hasSize(5);
        assertThat(page.sections).extracting(s -> s.type)
                .containsExactly("heroBanner", "eventList", "ticketGrid", "priceTrend", "rulePanel");

        // 模板翻面后 rulePanel 认内联展示形态（rules 文本数组），不再下发引用 ID
        var rules = page.sections.get(4);
        @SuppressWarnings("unchecked")
        var rulesProp = (java.util.List<String>) rules.props.get("rules");
        assertThat(rulesProp).containsExactly("凭电子票入场", "禁止携带外区食品");
        assertThat(rules.props).doesNotContainKey("ruleDocumentId");
        assertThat(rules.content).contains("凭电子票入场").contains("禁止携带外区食品");

        // 快照：渲染内容稳定（同一配置产出相同 HTML 片段）
        String json = objectMapper.writeValueAsString(page);
        var page2 = objectMapper.readValue(json, CampaignTemplateRenderer.RenderedPage.class);
        assertThat(page2.sections.get(0).content)
                .isEqualTo(page.sections.get(0).content)
                .contains("hero-banner")
                .contains("AWN F1 专场");
    }

    @Test
    void concertTemplateRenders() throws Exception {
        var config = CampaignTemplates.concert("AWN 音乐节专场", "演出票务",
                "asset_002", "event_002",
                java.util.List.of(java.util.Map.of(
                        "question", "如何取票？", "answer", "开演前 1 小时凭身份证取票")));

        validator.validate(config);

        var page = renderer.render(config);
        assertThat(page.template).isEqualTo("concert");
        assertThat(page.sections).hasSize(6);
        assertThat(page.sections).extracting(s -> s.type)
                .containsExactly("heroBanner", "eventList", "ticketGrid", "countdown", "faq", "noticeBar");

        // 模板翻面后 faq 认内联展示形态（entries 问答对），不再下发引用 ID
        var faq = page.sections.get(4);
        @SuppressWarnings("unchecked")
        var entries = (java.util.List<java.util.Map<String, String>>) faq.props.get("entries");
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0)).containsEntry("question", "如何取票？")
                .containsEntry("answer", "开演前 1 小时凭身份证取票");
        assertThat(faq.props).doesNotContainKey("faqId");
        assertThat(faq.content).contains("如何取票？").contains("开演前 1 小时凭身份证取票");

        String json = objectMapper.writeValueAsString(page);
        var page2 = objectMapper.readValue(json, CampaignTemplateRenderer.RenderedPage.class);
        assertThat(page2.sections.get(5).content)
                .isEqualTo(page.sections.get(5).content)
                .contains("notice-bar");
    }

    @Test
    void whitelistRejectsUnknownType() {
        var config = new site.awn.campaign.api.CampaignConfig(
                1, "any", null,
                java.util.List.of(new site.awn.campaign.api.CampaignConfig.SectionConfig(
                        "s", "customWidget", java.util.Map.of())));

        assertThatThrownBy(() -> validator.validate(config))
                .isInstanceOf(CampaignConfigValidationException.class)
                .hasMessageContaining("not in whitelist");
    }

    @Test
    void whitelistRejectsScriptAndIframe() {
        var script = new site.awn.campaign.api.CampaignConfig(
                1, "any", null,
                java.util.List.of(new site.awn.campaign.api.CampaignConfig.SectionConfig(
                        "s", "richText", java.util.Map.of("content", "<script>x</script>"))));
        var iframe = new site.awn.campaign.api.CampaignConfig(
                1, "any", null,
                java.util.List.of(new site.awn.campaign.api.CampaignConfig.SectionConfig(
                        "s", "videoEmbed", java.util.Map.of("url", "<iframe src=//evil>"))));

        assertThatThrownBy(() -> validator.validate(script))
                .isInstanceOf(CampaignConfigValidationException.class)
                .hasMessageContaining("script");
        assertThatThrownBy(() -> validator.validate(iframe))
                .isInstanceOf(CampaignConfigValidationException.class)
                .hasMessageContaining("embedded HTML");
    }

    @Test
    void duplicateSectionIdsAreRejected() {
        var config = new site.awn.campaign.api.CampaignConfig(
                1, "any", null,
                java.util.List.of(
                        new site.awn.campaign.api.CampaignConfig.SectionConfig("dup", "faq", java.util.Map.of()),
                        new site.awn.campaign.api.CampaignConfig.SectionConfig("dup", "noticeBar", java.util.Map.of())));

        assertThatThrownBy(() -> validator.validate(config))
                .isInstanceOf(CampaignConfigValidationException.class)
                .hasMessageContaining("unique");
    }

    @Test
    void schemaVersionMustBeOne() {
        var config = new site.awn.campaign.api.CampaignConfig(
                2, "any", null, java.util.List.of());

        assertThatThrownBy(() -> validator.validate(config))
                .isInstanceOf(CampaignConfigValidationException.class)
                .hasMessageContaining("schemaVersion");
    }

    @Test
    void whitelistCoversThirteenSectionTypes() {
        assertThat(site.awn.campaign.domain.SectionWhitelist.allowedTypes()).hasSize(13);
        assertThat(site.awn.campaign.domain.SectionWhitelist.allowedTypes()).contains(
                "heroBanner", "richText", "imageGallery", "videoEmbed",
                "eventList", "ticketGrid", "ticketComparison",
                "priceTrend", "channelComparison", "countdown",
                "rulePanel", "faq", "noticeBar");
    }

    @Test
    void resourceUrlsMustUseWhitelistedDomains() {
        assertThat(site.awn.campaign.domain.ResourceDomainWhitelist.isAllowed(
                "https://cdn.awn.site/f1/hero.jpg")).isTrue();
        assertThat(site.awn.campaign.domain.ResourceDomainWhitelist.isAllowed(
                "/static/hero.jpg")).isTrue();
        assertThat(site.awn.campaign.domain.ResourceDomainWhitelist.isAllowed(
                "http://cdn.awn.site/hero.jpg")).isFalse();
        assertThat(site.awn.campaign.domain.ResourceDomainWhitelist.isAllowed(
                "https://evil.example.com/hero.jpg")).isFalse();
        assertThat(site.awn.campaign.domain.ResourceDomainWhitelist.isAllowed(
                "https://cdn.awn.site.evil.com/hero.jpg")).isFalse();

        var badConfig = new site.awn.campaign.api.CampaignConfig(
                1, "any", null,
                java.util.List.of(new site.awn.campaign.api.CampaignConfig.SectionConfig(
                        "s", "videoEmbed",
                        java.util.Map.of("url", "https://evil.example.com/video.mp4"))));

        assertThatThrownBy(() -> validator.validate(badConfig))
                .isInstanceOf(CampaignConfigValidationException.class)
                .hasMessageContaining("non-whitelisted domain");
    }

    @Test
    void validatorRejectsScriptInsideNestedProps() {
        // faq entries[].answer 嵌套对象里的脚本标签不能漏过（校验器必须递归）
        var scriptInAnswer = new site.awn.campaign.api.CampaignConfig(
                1, "any", null,
                java.util.List.of(new site.awn.campaign.api.CampaignConfig.SectionConfig(
                        "s", "faq",
                        java.util.Map.of("entries", java.util.List.of(java.util.Map.of(
                                "question", "退票规则", "answer", "<script>alert(1)</script>"))))));

        assertThatThrownBy(() -> validator.validate(scriptInAnswer))
                .isInstanceOf(CampaignConfigValidationException.class)
                .hasMessageContaining("script");

        // 数组字符串项同样递归（rulePanel rules 文本数组）
        var scriptInRules = new site.awn.campaign.api.CampaignConfig(
                1, "any", null,
                java.util.List.of(new site.awn.campaign.api.CampaignConfig.SectionConfig(
                        "s", "rulePanel",
                        java.util.Map.of("rules", java.util.List.of("正常条款", "javascript:alert(1)")))));

        assertThatThrownBy(() -> validator.validate(scriptInRules))
                .isInstanceOf(CampaignConfigValidationException.class)
                .hasMessageContaining("script");
    }

    @Test
    void validatorRejectsNonWhitelistedUrlInsideNestedImages() {
        var config = new site.awn.campaign.api.CampaignConfig(
                1, "any", null,
                java.util.List.of(new site.awn.campaign.api.CampaignConfig.SectionConfig(
                        "s", "imageGallery",
                        java.util.Map.of("images", java.util.List.of(java.util.Map.of(
                                "src", "https://evil.example.com/hero.jpg", "alt", "现场图"))))));

        assertThatThrownBy(() -> validator.validate(config))
                .isInstanceOf(CampaignConfigValidationException.class)
                .hasMessageContaining("non-whitelisted domain");
    }

    @Test
    void validatorAcceptsInlineDisplayProps() {
        // 内联展示形态（rules / entries / images）为合法配置，全部深度校验通过
        var config = new site.awn.campaign.api.CampaignConfig(
                1, "concert", null,
                java.util.List.of(
                        new site.awn.campaign.api.CampaignConfig.SectionConfig(
                                "rules", "rulePanel",
                                java.util.Map.of("rules", java.util.List.of("凭电子票入场", "一米二以下儿童免票"))),
                        new site.awn.campaign.api.CampaignConfig.SectionConfig(
                                "faq", "faq",
                                java.util.Map.of("entries", java.util.List.of(java.util.Map.of(
                                        "question", "如何取票？", "answer", "开演前 1 小时凭身份证取票")))),
                        new site.awn.campaign.api.CampaignConfig.SectionConfig(
                                "gallery", "imageGallery",
                                java.util.Map.of("images", java.util.List.of(java.util.Map.of(
                                        "src", "https://cdn.awn.site/live/1.jpg", "alt", "现场图"))))));

        validator.validate(config);
    }
}
