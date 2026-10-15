package site.awn.catalog;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import site.awn.campaign.api.AddAssetCommand;
import site.awn.campaign.api.AddEventCommand;
import site.awn.campaign.api.AddFaqCommand;
import site.awn.campaign.api.AddProductCommand;
import site.awn.campaign.api.AddRuleCommand;
import site.awn.campaign.api.CampaignFacade;
import site.awn.campaign.api.CampaignRevisionView;
import site.awn.campaign.api.CampaignView;
import site.awn.campaign.api.CreateCampaignCommand;
import site.awn.campaign.api.UpdateCampaignCommand;
import site.awn.campaign.application.CampaignTemplates;
import site.awn.campaign.domain.CampaignConfigValidationException;
import site.awn.campaign.domain.CampaignNotFoundException;
import site.awn.catalog.api.CatalogFacade;
import site.awn.catalog.api.CreateCategoryCommand;
import site.awn.catalog.api.CreateProductCommand;
import site.awn.event.api.CreateEventCommand;
import site.awn.event.api.CreateVenueCommand;
import site.awn.event.api.EventFacade;
import site.awn.support.AbstractPostgresIntegrationTest;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 02：专场草稿、校验、发布版本（不可变 revision）。
 * 同类覆盖公开列表与须知 / 常见问题 / 素材内容（见 CampaignContentApiTests 的 HTTP 契约）。
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

    private String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    /** 建草稿 → 写配置 → 校验 → 发布（内容端点的可见性前提是 PUBLISHED） */
    private String publishedCampaign() {
        String suffix = suffix();
        String code = "camp-pub-" + suffix;
        campaigns.createCampaign(new CreateCampaignCommand(code, "已发布专场", "副标题"));
        campaigns.updateDraft(code, new UpdateCampaignCommand(null, null,
                CampaignTemplates.concert("专场 " + suffix, "内容测试", "asset-" + suffix,
                        "event-" + suffix, List.of(java.util.Map.of(
                                "question", "如何取票？", "answer", "开演前 1 小时取票")))));
        campaigns.validateCampaign(code);
        campaigns.publish(code);
        return code;
    }

    @Test
    void publicListShowsPublishedCampaignsOnly() {
        String suffix = suffix();
        String publishedFirst = publishedCampaign();
        campaigns.createCampaign(new CreateCampaignCommand("camp-list-" + suffix, "草稿专场", null));

        List<CampaignView> list = campaigns.publicCampaigns();

        assertThat(list).extracting(CampaignView::campaignCode)
                .contains(publishedFirst)
                .doesNotContain("camp-list-" + suffix);
        assertThat(list).allSatisfy(c -> assertThat(c.status()).isEqualTo("PUBLISHED"));
        // 最近变更在前：后建的已发布专场排在先建的之前
        String publishedLater = publishedCampaign();
        List<String> codes = campaigns.publicCampaigns().stream().map(CampaignView::campaignCode).toList();
        assertThat(codes.indexOf(publishedLater)).isLessThan(codes.indexOf(publishedFirst));
        // 已撤回不再出现在公开面
        campaigns.unpublish(publishedLater);
        assertThat(campaigns.publicCampaigns()).extracting(CampaignView::campaignCode)
                .doesNotContain(publishedLater);
    }

    @Test
    void contentEndpointsReturnEntriesInDisplayOrderWithResolvableIds() {
        String code = publishedCampaign();

        var second = campaigns.addRule(code, new AddRuleCommand("入场须知", "凭电子票入场", 5));
        var first = campaigns.addRule(code, new AddRuleCommand("退票须知", "开演前 48 小时可退", 1));
        var faq = campaigns.addFaq(code, new AddFaqCommand("如何取票？", "开演前 1 小时取票", 2));
        var asset = campaigns.addAsset(code, new AddAssetCommand(
                "IMAGE", "https://cdn.awn.site/live/" + suffix() + ".jpg", "现场图", 0));

        assertThat(campaigns.publicRules(code))
                .extracting(site.awn.campaign.api.CampaignRuleView::title)
                .containsExactly("退票须知", "入场须知");
        // 引用型 props 按 id 匹配：返回的 id 就是区块 props 里引用的 id
        assertThat(campaigns.publicRules(code)).extracting(site.awn.campaign.api.CampaignRuleView::id)
                .containsExactly(first.id(), second.id());
        assertThat(campaigns.publicFaq(code)).singleElement()
                .satisfies(f -> assertThat(f.question()).isEqualTo("如何取票？"));
        assertThat(campaigns.publicAssets(code)).singleElement()
                .satisfies(a -> {
                    assertThat(a.id()).isEqualTo(asset.id());
                    assertThat(a.assetType()).isEqualTo("IMAGE");
                    assertThat(a.url()).endsWith(".jpg");
                    assertThat(a.altText()).isEqualTo("现场图");
                });

        // 三类内容同一排序口径（display_order，同序按 id）
        campaigns.addFaq(code, new AddFaqCommand("末位问题？", "答", 99));
        campaigns.addFaq(code, new AddFaqCommand("首位问题？", "答", 1));
        campaigns.addAsset(code, new AddAssetCommand(
                "BANNER", "https://cdn.awn.site/banner/" + suffix() + ".jpg", "横幅", -1));
        assertThat(campaigns.publicFaq(code)).extracting(
                        site.awn.campaign.api.CampaignFaqView::question)
                .containsExactly("首位问题？", "如何取票？", "末位问题？");
        assertThat(campaigns.publicAssets(code)).extracting(
                        site.awn.campaign.api.CampaignAssetView::altText)
                .containsExactly("横幅", "现场图");
    }

    @Test
    void contentReadsHideDraftsAndUnknownCampaigns() {
        String suffix = suffix();
        String draft = "camp-draft-" + suffix;
        campaigns.createCampaign(new CreateCampaignCommand(draft, "草稿", null));
        campaigns.addRule(draft, new AddRuleCommand("草稿须知", "草稿内容", null));

        // 草稿内容不泄露：与未知专场同口径按不存在处理
        assertThatThrownBy(() -> campaigns.publicRules(draft))
                .isInstanceOf(CampaignNotFoundException.class);
        assertThatThrownBy(() -> campaigns.publicFaq(draft))
                .isInstanceOf(CampaignNotFoundException.class);
        assertThatThrownBy(() -> campaigns.publicAssets(draft))
                .isInstanceOf(CampaignNotFoundException.class);

        String missing = "camp-none-" + suffix;
        assertThatThrownBy(() -> campaigns.publicRules(missing))
                .isInstanceOf(CampaignNotFoundException.class);
    }

    @Test
    void deletingContentOnlyAffectsItsOwnCampaign() {
        String owner = publishedCampaign();
        String other = publishedCampaign();
        var rule = campaigns.addRule(owner, new AddRuleCommand("须知", "内容", null));
        var faq = campaigns.addFaq(owner, new AddFaqCommand("问？", "答", null));
        var asset = campaigns.addAsset(other, new AddAssetCommand(
                "BANNER", "https://cdn.awn.site/banner/" + suffix() + ".jpg", null, 0));

        campaigns.removeRule(other, rule.id());
        campaigns.removeFaq(other, faq.id());
        campaigns.removeAsset(owner, asset.id());

        // 他专场的删除请求不受理，归属专场条目仍在
        assertThat(campaigns.publicRules(owner)).singleElement()
                .satisfies(r -> assertThat(r.id()).isEqualTo(rule.id()));
        assertThat(campaigns.publicFaq(owner)).singleElement()
                .satisfies(f -> assertThat(f.id()).isEqualTo(faq.id()));
        assertThat(campaigns.publicAssets(other)).singleElement()
                .satisfies(a -> assertThat(a.id()).isEqualTo(asset.id()));

        // 归属专场删除生效
        campaigns.removeRule(owner, rule.id());
        assertThat(campaigns.publicRules(owner)).isEmpty();
    }

    @Test
    void blankContentAndUnknownAssetTypeAreRejected() {
        String code = publishedCampaign();

        assertThatThrownBy(() -> campaigns.addRule(code, new AddRuleCommand(" ", "内容", null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> campaigns.addRule(code, new AddRuleCommand("须知", "   ", null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> campaigns.addFaq(code, new AddFaqCommand(null, "答", null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> campaigns.addAsset(code, new AddAssetCommand(
                "HOLOGRAM", "https://cdn.awn.site/x.jpg", null, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> campaigns.addAsset(code, new AddAssetCommand(
                "IMAGE", "  ", null, null)))
                .isInstanceOf(IllegalArgumentException.class);

        String missing = "camp-none-" + suffix();
        assertThatThrownBy(() -> campaigns.addRule(missing, new AddRuleCommand("须知", "内容", null)))
                .isInstanceOf(CampaignNotFoundException.class);

        // 排序缺省为 0（追加在末尾），条目仍可写入
        assertThat(campaigns.addRule(code, new AddRuleCommand("须知", "内容", null)).id()).isNotNull();
        assertThat(campaigns.addFaq(code, new AddFaqCommand("问？", "答", null)).id()).isNotNull();
        assertThat(campaigns.addAsset(code, new AddAssetCommand(
                "IMAGE", "https://cdn.awn.site/x.jpg", "图", null)).displayOrder()).isZero();
    }
}
