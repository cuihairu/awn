package site.awn.campaign.api;

/**
 * 更新专场草稿命令。
 */
public record UpdateCampaignCommand(
        String title,
        String subtitle,
        CampaignConfig config
) {
}
