package site.awn.campaign.api;

/**
 * 常见问题条目视图（公开读与运营配置共用同一形态）。
 *
 * @param id 引用型 props（faqId）按此 id 匹配
 */
public record CampaignFaqView(
        Long id,
        String question,
        String answer
) {
}