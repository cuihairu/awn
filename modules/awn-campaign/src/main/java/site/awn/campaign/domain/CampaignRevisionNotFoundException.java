package site.awn.campaign.domain;

/**
 * 专场版本不存在。
 */
public class CampaignRevisionNotFoundException extends RuntimeException {

    public CampaignRevisionNotFoundException(Long revisionId) {
        super("campaign revision not found: " + revisionId);
    }
}
