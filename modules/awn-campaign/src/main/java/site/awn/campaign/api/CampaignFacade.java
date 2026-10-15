package site.awn.campaign.api;

import java.util.List;

/**
 * 专场 Facade。
 * 专场通过"商品引用"组织交易商品，自身不拥有库存和订单实现。
 */
public interface CampaignFacade {

    CampaignView createCampaign(CreateCampaignCommand command);

    CampaignView campaign(String campaignCode);

    /**
     * 公开专场列表：只含 PUBLISHED，按最近变更倒序。
     * 草稿 / 已撤回 / 已归档不出现在公开面。
     */
    List<CampaignView> publicCampaigns();

    /**
     * 公开须知条目（按录入顺序）。可见性与渲染同口径：专场未发布或未知时抛
     * {@link site.awn.campaign.domain.CampaignNotFoundException}，草稿内容不泄露。
     */
    List<CampaignRuleView> publicRules(String campaignCode);

    /** 公开常见问题条目（按录入顺序）；可见性同 {@link #publicRules(String)}。 */
    List<CampaignFaqView> publicFaq(String campaignCode);

    /** 公开素材条目（按录入顺序）；可见性同 {@link #publicRules(String)}。 */
    List<CampaignAssetView> publicAssets(String campaignCode);

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

    /** 录入素材条目（运营配置）；素材类型不在白名单时抛 IllegalArgumentException。 */
    CampaignAssetView addAsset(String campaignCode, AddAssetCommand command);

    /** 删除素材条目：只认归属专场，他专场条目不受理。 */
    void removeAsset(String campaignCode, Long assetId);

    /** 录入须知条目（运营配置）；专场未知时抛 CampaignNotFoundException。 */
    CampaignRuleView addRule(String campaignCode, AddRuleCommand command);

    /** 删除须知条目：只认归属专场，他专场条目不受理。 */
    void removeRule(String campaignCode, Long ruleId);

    /** 录入常见问题条目（运营配置）；专场未知时抛 CampaignNotFoundException。 */
    CampaignFaqView addFaq(String campaignCode, AddFaqCommand command);

    /** 删除常见问题条目：只认归属专场，他专场条目不受理。 */
    void removeFaq(String campaignCode, Long faqId);
}
