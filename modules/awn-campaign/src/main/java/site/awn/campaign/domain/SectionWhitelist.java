package site.awn.campaign.domain;

import java.util.Set;

/**
 * 专场页面模块白名单。
 * 初期只提供受控的模块白名单，禁止注入任意 JavaScript/HTML。
 */
public final class SectionWhitelist {

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "heroBanner",
            "richText",
            "imageGallery",
            "videoEmbed",
            "eventList",
            "ticketGrid",
            "ticketComparison",
            "priceTrend",
            "channelComparison",
            "countdown",
            "rulePanel",
            "faq",
            "noticeBar"
    );

    private SectionWhitelist() {
    }

    public static boolean isAllowed(String type) {
        return ALLOWED_TYPES.contains(type);
    }

    public static Set<String> allowedTypes() {
        return ALLOWED_TYPES;
    }
}
