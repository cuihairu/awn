package site.awn.campaign.domain;

/**
 * 专场不存在。
 */
public class CampaignNotFoundException extends RuntimeException {

    public CampaignNotFoundException(String campaignCode) {
        super("campaign not found: " + campaignCode);
    }
}
