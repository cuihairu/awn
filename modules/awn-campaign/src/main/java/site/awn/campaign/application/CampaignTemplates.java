package site.awn.campaign.application;

import site.awn.campaign.api.CampaignConfig;
import site.awn.campaign.api.CampaignConfig.SectionConfig;

import java.util.List;
import java.util.Map;

/**
 * 预定义专场模板。
 * 提供至少两种模板：motorsport（F1 专场）和 concert（演唱会专场）。
 */
public final class CampaignTemplates {

    private CampaignTemplates() {
    }

    /**
     * F1 赛车专场模板。
     * rulePanel 下发内联展示形态（rules 文本数组，逐条渲染），不下发文档引用 ID
     * （ruleDocumentId 无可查端点，引用形态在公开面渲染为空白）。
     */
    public static CampaignConfig motorsport(String title, String description, String heroAssetId,
                                             String eventId, List<String> rules) {
        return new CampaignConfig(
                1,
                "motorsport",
                new CampaignConfig.SeoConfig(title, description),
                List.of(
                        new SectionConfig("hero", "heroBanner",
                                Map.of("assetId", heroAssetId, "title", title)),
                        new SectionConfig("event-list", "eventList",
                                Map.of("eventIds", List.of(eventId))),
                        new SectionConfig("tickets", "ticketGrid",
                                Map.of("eventIds", List.of(eventId))),
                        new SectionConfig("price-history", "priceTrend",
                                Map.of("eventId", eventId)),
                        new SectionConfig("rules", "rulePanel",
                                Map.of("rules", rules))
                )
        );
    }

    /**
     * 演唱会专场模板。
     * faq 下发内联展示形态（entries 问答对，question / answer 口径与渲染包一致），
     * 不下发引用 ID（faqId 无可查端点）。
     */
    public static CampaignConfig concert(String title, String description, String heroAssetId,
                                          String eventId, List<Map<String, String>> entries) {
        return new CampaignConfig(
                1,
                "concert",
                new CampaignConfig.SeoConfig(title, description),
                List.of(
                        new SectionConfig("hero", "heroBanner",
                                Map.of("assetId", heroAssetId, "title", title)),
                        new SectionConfig("event-list", "eventList",
                                Map.of("eventIds", List.of(eventId))),
                        new SectionConfig("tickets", "ticketGrid",
                                Map.of("eventIds", List.of(eventId))),
                        new SectionConfig("countdown", "countdown",
                                Map.of("endTime", "2026-12-31T20:00:00Z")),
                        new SectionConfig("faq", "faq",
                                Map.of("entries", entries)),
                        new SectionConfig("notice", "noticeBar",
                                Map.of("message", "本场次门票数量有限，请尽快购买"))
                )
        );
    }
}
