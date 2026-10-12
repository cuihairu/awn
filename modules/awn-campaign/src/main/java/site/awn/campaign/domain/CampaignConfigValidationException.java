package site.awn.campaign.domain;

/**
 * 专场配置校验失败。
 */
public class CampaignConfigValidationException extends RuntimeException {

    public CampaignConfigValidationException(String message) {
        super(message);
    }
}
