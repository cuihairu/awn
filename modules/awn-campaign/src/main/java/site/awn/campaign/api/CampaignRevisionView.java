package site.awn.campaign.api;

/**
 * 专场版本视图。
 */
public record CampaignRevisionView(
        Long id,
        Long campaignId,
        Integer revisionNumber,
        String configSnapshot
) {
    public static CampaignRevisionView of(Long id, Long campaignId, Integer revisionNumber, String configSnapshot) {
        return new CampaignRevisionView(id, campaignId, revisionNumber, configSnapshot);
    }
}
