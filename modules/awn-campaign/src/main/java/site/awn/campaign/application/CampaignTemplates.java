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
     */
    public static CampaignConfig motorsport(String title, String description, String heroAssetId,
                                             String eventId, String rulesDocumentId) {
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
                                Map.of("ruleDocumentId", rulesDocumentId))
                )
        );
    }

    /**
     * 演唱会专场模板。
     */
    public static CampaignConfig concert(String title, String description, String heroAssetId,
                                          String eventId, String faqId) {
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
                                Map.of("faqId", faqId)),
                        new SectionConfig("notice", "noticeBar",
                                Map.of("message", "本场次门票数量有限，请尽快购买"))
                )
        );
    }
}
