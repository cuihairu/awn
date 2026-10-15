package site.awn.campaign.api;

/**
 * 素材视图（公开读与运营配置共用同一形态）。
 *
 * <p>素材红线：url 只能指向有授权来源或明确标注的占位图，本模块不生成图、不代餐 logo；
 * altText 缺失时前端出占位块（见 docs/api/contracts.md「专场列表与内容接口」）。
 *
 * @param id 引用型 props（assetIds）按此 id 匹配
 */
public record CampaignAssetView(
        Long id,
        String assetType,
        String url,
        String altText,
        Integer displayOrder
) {
}