package site.awn.campaign.api;

import java.util.List;

/**
 * 专场 Facade。
 * 专场通过"商品引用"组织交易商品，自身不拥有库存和订单实现。
 */
public interface CampaignFacade {

    CampaignView createCampaign(CreateCampaignCommand command);

    CampaignView campaign(String campaignCode);

    CampaignView updateDraft(String campaignCode, UpdateCampaignCommand command);

    void validateCampaign(String campaignCode);

    CampaignRevisionView publish(String campaignCode);

    void unpublish(String campaignCode);

    void archive(String campaignCode);

    CampaignRevisionView publishedRevision(String campaignCode);

    RenderedPageView renderPublished(String campaignCode);

    List<CampaignRevisionView> revisions(String campaignCode);

    void addEvent(String campaignCode, AddEventCommand command);

    void removeEvent(String campaignCode, Long eventId);

    void addProduct(String campaignCode, AddProductCommand command);

    void removeProduct(String campaignCode, Long productId);

    void addAsset(String campaignCode, AddAssetCommand command);

    void removeAsset(String campaignCode, Long assetId);
}
