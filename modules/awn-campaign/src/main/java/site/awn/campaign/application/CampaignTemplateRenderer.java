package site.awn.campaign.application;

import org.springframework.stereotype.Component;
import site.awn.campaign.api.CampaignConfig;
import site.awn.campaign.api.CampaignConfig.SectionConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 专场模板渲染器。
 * 根据配置渲染页面模块，至少支持两种模板：motorsport（F1 专场）和 concert（演唱会专场）。
 */
@Component
public class CampaignTemplateRenderer {

    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    public CampaignTemplateRenderer(com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public RenderedPage render(CampaignConfig config) {
        if (config == null || config.template() == null) {
            throw new IllegalArgumentException("config and template must not be null");
        }

        RenderedPage page = new RenderedPage();
        page.schemaVersion = config.schemaVersion();
        page.template = config.template();
        page.seo = config.seo() != null ? new RenderedSeo(config.seo().title(), config.seo().description()) : null;
        page.sections = new ArrayList<>();

        if (config.sections() != null) {
            for (SectionConfig section : config.sections()) {
                RenderedSection rendered = renderSection(section);
                page.sections.add(rendered);
            }
        }

        return page;
    }

    private RenderedSection renderSection(SectionConfig section) {
        RenderedSection rendered = new RenderedSection();
        rendered.id = section.id();
        rendered.type = section.type();
        rendered.props = new HashMap<>(section.props() != null ? section.props() : Map.of());
        rendered.content = generateContent(section.type(), section.props());
        return rendered;
    }

    private String generateContent(String type, Map<String, Object> props) {
        return switch (type) {
            case "heroBanner" -> generateHeroBanner(props);
            case "richText" -> generateRichText(props);
            case "imageGallery" -> generateImageGallery(props);
            case "videoEmbed" -> generateVideoEmbed(props);
            case "eventList" -> generateEventList(props);
            case "ticketGrid" -> generateTicketGrid(props);
            case "ticketComparison" -> generateTicketComparison(props);
            case "priceTrend" -> generatePriceTrend(props);
            case "channelComparison" -> generateChannelComparison(props);
            case "countdown" -> generateCountdown(props);
            case "rulePanel" -> generateRulePanel(props);
            case "faq" -> generateFaq(props);
            case "noticeBar" -> generateNoticeBar(props);
            default -> throw new IllegalArgumentException("unknown section type: " + type);
        };
    }

    private String generateHeroBanner(Map<String, Object> props) {
        String assetId = getString(props, "assetId", "");
        String title = getString(props, "title", "");
        return "<div class=\"hero-banner\" data-asset=\"" + assetId + "\">"
                + "<h1>" + escapeHtml(title) + "</h1>"
                + "</div>";
    }

    private String generateRichText(Map<String, Object> props) {
        String content = getString(props, "content", "");
        return "<div class=\"rich-text\">" + escapeHtml(content) + "</div>";
    }

    private String generateImageGallery(Map<String, Object> props) {
        String assetIds = getString(props, "assetIds", "[]");
        return "<div class=\"image-gallery\" data-assets=\"" + assetIds + "\"></div>";
    }

    private String generateVideoEmbed(Map<String, Object> props) {
        String url = getString(props, "url", "");
        return "<div class=\"video-embed\" data-url=\"" + escapeHtml(url) + "\"></div>";
    }

    private String generateEventList(Map<String, Object> props) {
        String eventIds = getString(props, "eventIds", "[]");
        return "<div class=\"event-list\" data-events=\"" + eventIds + "\"></div>";
    }

    private String generateTicketGrid(Map<String, Object> props) {
        String eventIds = getString(props, "eventIds", "[]");
        return "<div class=\"ticket-grid\" data-events=\"" + eventIds + "\"></div>";
    }

    private String generateTicketComparison(Map<String, Object> props) {
        String eventIds = getString(props, "eventIds", "[]");
        return "<div class=\"ticket-comparison\" data-events=\"" + eventIds + "\"></div>";
    }

    private String generatePriceTrend(Map<String, Object> props) {
        String eventId = getString(props, "eventId", "");
        return "<div class=\"price-trend\" data-event=\"" + eventId + "\"></div>";
    }

    private String generateChannelComparison(Map<String, Object> props) {
        String eventId = getString(props, "eventId", "");
        return "<div class=\"channel-comparison\" data-event=\"" + eventId + "\"></div>";
    }

    private String generateCountdown(Map<String, Object> props) {
        String endTime = getString(props, "endTime", "");
        return "<div class=\"countdown\" data-end=\"" + endTime + "\"></div>";
    }

    private String generateRulePanel(Map<String, Object> props) {
        String ruleDocumentId = getString(props, "ruleDocumentId", "");
        return "<div class=\"rule-panel\" data-rules=\"" + ruleDocumentId + "\"></div>";
    }

    private String generateFaq(Map<String, Object> props) {
        String faqId = getString(props, "faqId", "");
        return "<div class=\"faq\" data-faq=\"" + faqId + "\"></div>";
    }

    private String generateNoticeBar(Map<String, Object> props) {
        String message = getString(props, "message", "");
        return "<div class=\"notice-bar\">" + escapeHtml(message) + "</div>";
    }

    private String getString(Map<String, Object> props, String key, String defaultValue) {
        Object value = props.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }

    public static class RenderedPage {
        public Integer schemaVersion;
        public String template;
        public RenderedSeo seo;
        public List<RenderedSection> sections;
    }

    public static class RenderedSeo {
        public String title;
        public String description;

        public RenderedSeo(String title, String description) {
            this.title = title;
            this.description = description;
        }
    }

    public static class RenderedSection {
        public String id;
        public String type;
        public Map<String, Object> props;
        public String content;
    }
}
