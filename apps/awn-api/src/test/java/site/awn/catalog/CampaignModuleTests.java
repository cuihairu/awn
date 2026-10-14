package site.awn.catalog;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import site.awn.campaign.api.AddEventCommand;
import site.awn.campaign.api.AddProductCommand;
import site.awn.campaign.api.CampaignFacade;
import site.awn.campaign.api.CampaignRevisionView;
import site.awn.campaign.api.CampaignView;
import site.awn.campaign.api.CreateCampaignCommand;
import site.awn.campaign.api.UpdateCampaignCommand;
import site.awn.campaign.application.CampaignTemplates;
import site.awn.campaign.domain.CampaignConfigValidationException;
import site.awn.catalog.api.CatalogFacade;
import site.awn.catalog.api.CreateCategoryCommand;
import site.awn.catalog.api.CreateProductCommand;
import site.awn.event.api.CreateEventCommand;
import site.awn.event.api.CreateVenueCommand;
import site.awn.event.api.EventFacade;
import site.awn.support.AbstractPostgresIntegrationTest;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 02：专场草稿、校验、发布版本（不可变 revision）。
 */
@SpringBootTest
class CampaignModuleTests extends AbstractPostgresIntegrationTest {

    @Autowired CampaignFacade campaigns;
    @Autowired EventFacade events;
    @Autowired CatalogFacade catalog;

    @Test
    void draftValidatePublishGeneratesImmutableRevision() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        CampaignView campaign = campaigns.createCampaign(
                new CreateCampaignCommand("camp-" + suffix, "F1 专场", "赛事票务"));
        assertThat(campaign.status()).isEqualTo("DRAFT");

        // 草稿写入配置
        var config = CampaignTemplates.motorsport("AWN F1 专场", "查看赛事与票务",
                "asset_" + suffix, "event-" + suffix, java.util.List.of("凭电子票入场"));
        campaigns.updateDraft("camp-" + suffix, new UpdateCampaignCommand(null, null, config));

        // 未校验直接发布被拒绝
        assertThatThrownBy(() -> campaigns.publish("camp-" + suffix))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("validated");

        // 校验后发布
        campaigns.validateCampaign("camp-" + suffix);
        CampaignRevisionView revision = campaigns.publish("camp-" + suffix);
        assertThat(revision.revisionNumber()).isEqualTo(1);
        assertThat(revision.configSnapshot()).contains("motorsport");

        // 已发布版本可读取
        CampaignRevisionView published = campaigns.publishedRevision("camp-" + suffix);
        assertThat(published.revisionNumber()).isEqualTo(1);

        // 草稿修改不影响已发布版本
        var newConfig = CampaignTemplates.concert("AWN 音乐节", "另一模板",
                "asset_" + suffix, "event-" + suffix,
                java.util.List.of(java.util.Map.of("question", "如何取票？", "answer", "开演前 1 小时取票")));
        campaigns.updateDraft("camp-" + suffix, new UpdateCampaignCommand(null, null, newConfig));

        CampaignRevisionView stillPublished = campaigns.publishedRevision("camp-" + suffix);
        assertThat(stillPublished.revisionNumber()).isEqualTo(1);
        assertThat(stillPublished.configSnapshot()).contains("motorsport");

        // 重新校验并发布生成新版本
        campaigns.validateCampaign("camp-" + suffix);
        CampaignRevisionView revision2 = campaigns.publish("camp-" + suffix);
        assertThat(revision2.revisionNumber()).isEqualTo(2);
        assertThat(revision2.configSnapshot()).contains("concert");
    }

    @Test
    void invalidSectionTypeIsRejected() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        campaigns.createCampaign(new CreateCampaignCommand("camp-bad-" + suffix, "坏专场", null));

        var config = new site.awn.campaign.api.CampaignConfig(
                1, "custom",
                new site.awn.campaign.api.CampaignConfig.SeoConfig("t", "d"),
                java.util.List.of(new site.awn.campaign.api.CampaignConfig.SectionConfig(
                        "s1", "notInWhitelist", java.util.Map.of())));

        assertThatThrownBy(() -> campaigns.updateDraft("camp-bad-" + suffix,
                new UpdateCampaignCommand(null, null, config)))
                .isInstanceOf(CampaignConfigValidationException.class)
                .hasMessageContaining("not in whitelist");
    }

    @Test
    void scriptInjectionIsRejected() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        campaigns.createCampaign(new CreateCampaignCommand("camp-xss-" + suffix, "XSS 专场", null));

        var config = new site.awn.campaign.api.CampaignConfig(
                1, "custom",
                null,
                java.util.List.of(new site.awn.campaign.api.CampaignConfig.SectionConfig(
                        "s1", "richText",
                        java.util.Map.of("content", "<script>alert(1)</script>"))));

        assertThatThrownBy(() -> campaigns.updateDraft("camp-xss-" + suffix,
                new UpdateCampaignCommand(null, null, config)))
                .isInstanceOf(CampaignConfigValidationException.class)
                .hasMessageContaining("script");
    }

    @Test
    void renderPublishedReturnsPageFromImmutableRevision() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        campaigns.createCampaign(new CreateCampaignCommand("camp-render-" + suffix, "渲染专场", null));

        var config = CampaignTemplates.concert("AWN 演唱会", "渲染测试",
                "asset_r", "event_r",
                java.util.List.of(java.util.Map.of("question", "如何取票？", "answer", "开演前 1 小时取票")));
        campaigns.updateDraft("camp-render-" + suffix, new UpdateCampaignCommand(null, null, config));
        campaigns.validateCampaign("camp-render-" + suffix);
        campaigns.publish("camp-render-" + suffix);

        // 渲染读取已发布版本
        var page = campaigns.renderPublished("camp-render-" + suffix);
        assertThat(page.template()).isEqualTo("concert");
        assertThat(page.schemaVersion()).isEqualTo(1);
        assertThat(page.seoTitle()).isEqualTo("AWN 演唱会");
        assertThat(page.sections()).extracting(site.awn.campaign.api.RenderedSectionView::type)
                .containsExactly("heroBanner", "eventList", "ticketGrid", "countdown", "faq", "noticeBar");

        // 草稿修改不影响渲染（只读已发布版本）
        var newConfig = CampaignTemplates.motorsport("AWN F1", "新模板", "a", "e", java.util.List.of("r"));
        campaigns.updateDraft("camp-render-" + suffix, new UpdateCampaignCommand(null, null, newConfig));
        var pageStill = campaigns.renderPublished("camp-render-" + suffix);
        assertThat(pageStill.template()).isEqualTo("concert");
    }

    @Test
    void sameProductInMultipleCampaignsSharesInventory() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        // 创建活动与商品
        var venue = events.createVenue(new CreateVenueCommand("v-" + suffix, "场馆", null, null));
        Instant start = Instant.now().plusSeconds(3600);
        var event = events.createEvent(new CreateEventCommand("e-" + suffix, "活动", null,
                start, start.plusSeconds(7200), venue.id()));
        var category = catalog.createCategory(new CreateCategoryCommand("c-" + suffix, "票档", null));
        var product = catalog.createProduct(new CreateProductCommand(
                "p-" + suffix, event.id(), null, category.id(), "商品-" + suffix, 100L));

        // 两个专场引用同一商品
        campaigns.createCampaign(new CreateCampaignCommand("camp-a-" + suffix, "专场 A", null));
        campaigns.createCampaign(new CreateCampaignCommand("camp-b-" + suffix, "专场 B", null));
        campaigns.addEvent("camp-a-" + suffix, new AddEventCommand(event.id(), 0));
        campaigns.addEvent("camp-b-" + suffix, new AddEventCommand(event.id(), 0));
        campaigns.addProduct("camp-a-" + suffix, new AddProductCommand(product.id(), 0));
        campaigns.addProduct("camp-b-" + suffix, new AddProductCommand(product.id(), 0));

        // 两专场均引用成功——库存只在 product 关联的池子上，不因专场复制
        assertThat(campaigns.campaign("camp-a-" + suffix).status()).isEqualTo("DRAFT");
        assertThat(campaigns.campaign("camp-b-" + suffix).status()).isEqualTo("DRAFT");
    }
}
