package site.awn.campaign.api;

/**
 * 须知条目视图（公开读与运营配置共用同一形态）。
 *
 * @param id 引用型 props（ruleDocumentId）按此 id 匹配
 */
public record CampaignRuleView(
        Long id,
        String title,
        String content
) {
}