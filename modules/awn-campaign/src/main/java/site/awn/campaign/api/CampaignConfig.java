package site.awn.campaign.api;

import java.util.List;

/**
 * 专场页面配置。
 * 页面配置以 JSON Schema 描述，由前端"专场模板渲染器"按版本渲染。
 */
public record CampaignConfig(
        Integer schemaVersion,
        String template,
        SeoConfig seo,
        List<SectionConfig> sections
) {
    public record SeoConfig(
            String title,
            String description
    ) {
    }

    public record SectionConfig(
            String id,
            String type,
            java.util.Map<String, Object> props
    ) {
    }
}
