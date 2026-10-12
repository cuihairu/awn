package site.awn.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
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
                "asset_001", "event_001", "rules_001");

        validator.validate(config);

        var page = renderer.render(config);
        assertThat(page.template).isEqualTo("motorsport");
        assertThat(page.sections).hasSize(5);
        assertThat(page.sections).extracting(s -> s.type)
                .containsExactly("heroBanner", "eventList", "ticketGrid", "priceTrend", "rulePanel");

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
                "asset_002", "event_002", "faq_002");

        validator.validate(config);

        var page = renderer.render(config);
        assertThat(page.template).isEqualTo("concert");
        assertThat(page.sections).hasSize(6);
        assertThat(page.sections).extracting(s -> s.type)
                .containsExactly("heroBanner", "eventList", "ticketGrid", "countdown", "faq", "noticeBar");

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
}
