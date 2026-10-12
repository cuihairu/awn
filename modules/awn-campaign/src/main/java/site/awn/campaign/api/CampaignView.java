package site.awn.campaign.api;

/**
 * 专场视图。
 */
public record CampaignView(
        Long id,
        String campaignCode,
        String title,
        String subtitle,
        String status,
        Long currentRevisionId
) {
    public static CampaignView of(Long id, String campaignCode, String title, String subtitle,
                                   String status, Long currentRevisionId) {
        return new CampaignView(id, campaignCode, title, subtitle, status, currentRevisionId);
    }
}
