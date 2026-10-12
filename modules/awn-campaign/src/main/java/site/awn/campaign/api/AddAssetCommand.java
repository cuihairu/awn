package site.awn.campaign.api;

/**
 * 添加专场资源命令。
 */
public record AddAssetCommand(
        String assetType,
        String url,
        String altText,
        Integer displayOrder
) {
}
