package site.awn.campaign.api;

/**
 * 创建专场命令。
 */
public record CreateCampaignCommand(
        String campaignCode,
        String title,
        String subtitle
) {
}
