package site.awn.campaign.application;

import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.awn.campaign.api.AddAssetCommand;
import site.awn.campaign.api.AddEventCommand;
import site.awn.campaign.api.AddFaqCommand;
import site.awn.campaign.api.AddProductCommand;
import site.awn.campaign.api.AddRuleCommand;
import site.awn.campaign.api.CampaignAssetView;
import site.awn.campaign.api.CampaignConfig;
import site.awn.campaign.api.CampaignFacade;
import site.awn.campaign.api.CampaignFaqView;
import site.awn.campaign.api.CampaignRevisionView;
import site.awn.campaign.api.CampaignRuleView;
import site.awn.campaign.api.CampaignView;
import site.awn.campaign.api.CreateCampaignCommand;
import site.awn.campaign.api.RenderedPageView;
import site.awn.campaign.api.RenderedSectionView;
import site.awn.campaign.api.UpdateCampaignCommand;
import site.awn.campaign.domain.Campaign;
import site.awn.campaign.domain.CampaignAsset;
import site.awn.campaign.domain.CampaignConfigValidationException;
import site.awn.campaign.domain.CampaignConfigValidator;
import site.awn.campaign.domain.CampaignEvent;
import site.awn.campaign.domain.CampaignFaq;
import site.awn.campaign.domain.CampaignNotFoundException;
import site.awn.campaign.domain.CampaignProduct;
import site.awn.campaign.domain.CampaignRevision;
import site.awn.campaign.domain.CampaignRevisionNotFoundException;
import site.awn.campaign.domain.CampaignRuleDocument;
import site.awn.campaign.domain.CampaignStatus;
import site.awn.campaign.infrastructure.CampaignAssetRepository;
import site.awn.campaign.infrastructure.CampaignEventRepository;
import site.awn.campaign.infrastructure.CampaignFaqRepository;
import site.awn.campaign.infrastructure.CampaignProductRepository;
import site.awn.campaign.infrastructure.CampaignRepository;
import site.awn.campaign.infrastructure.CampaignRevisionRepository;
import site.awn.campaign.infrastructure.CampaignRuleDocumentRepository;

import java.util.List;

@Service
public class CampaignFacadeImpl implements CampaignFacade {

    private final CampaignRepository campaigns;
    private final CampaignRevisionRepository revisions;
    private final CampaignEventRepository campaignEvents;
    private final CampaignProductRepository campaignProducts;
    private final CampaignAssetRepository campaignAssets;
    private final CampaignRuleDocumentRepository campaignRules;
    private final CampaignFaqRepository campaignFaqs;
    private final CampaignConfigValidator configValidator;
    private final CampaignTemplateRenderer renderer;
    private final ObjectMapper objectMapper;

    public CampaignFacadeImpl(CampaignRepository campaigns,
                              CampaignRevisionRepository revisions,
                              CampaignEventRepository campaignEvents,
                              CampaignProductRepository campaignProducts,
                              CampaignAssetRepository campaignAssets,
                              CampaignRuleDocumentRepository campaignRules,
                              CampaignFaqRepository campaignFaqs,
                              CampaignConfigValidator configValidator,
                              CampaignTemplateRenderer renderer,
                              ObjectMapper objectMapper) {
        this.campaigns = campaigns;
        this.revisions = revisions;
        this.campaignEvents = campaignEvents;
        this.campaignProducts = campaignProducts;
        this.campaignAssets = campaignAssets;
        this.campaignRules = campaignRules;
        this.campaignFaqs = campaignFaqs;
        this.configValidator = configValidator;
        this.renderer = renderer;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public CampaignView createCampaign(CreateCampaignCommand command) {
        Campaign campaign = new Campaign(command.campaignCode(), command.title(), command.subtitle());
        return toView(campaigns.save(campaign));
    }

    @Override
    @Transactional(readOnly = true)
    public CampaignView campaign(String campaignCode) {
        return campaigns.findByCampaignCode(campaignCode)
                .map(this::toView)
                .orElseThrow(() -> new CampaignNotFoundException(campaignCode));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CampaignView> publicCampaigns() {
        // 公开面只看 PUBLISHED：草稿、已撤回、已归档都不出现。
        // 排序用 campaigns.updated_at（发布与草稿改动都会刷新它），同值按 id 稳定。
        return campaigns.findByStatusOrderByUpdatedAtDescIdDesc(CampaignStatus.PUBLISHED).stream()
                .map(this::toView)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CampaignRuleView> publicRules(String campaignCode) {
        Campaign campaign = requirePublished(campaignCode);
        return campaignRules.findByCampaignIdOrderByDisplayOrderAscIdAsc(campaign.getId()).stream()
                .map(rule -> new CampaignRuleView(rule.getId(), rule.getTitle(), rule.getContent()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CampaignFaqView> publicFaq(String campaignCode) {
        Campaign campaign = requirePublished(campaignCode);
        return campaignFaqs.findByCampaignIdOrderByDisplayOrderAscIdAsc(campaign.getId()).stream()
                .map(faq -> new CampaignFaqView(faq.getId(), faq.getQuestion(), faq.getAnswer()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CampaignAssetView> publicAssets(String campaignCode) {
        Campaign campaign = requirePublished(campaignCode);
        return campaignAssets.findByCampaignIdOrderByDisplayOrderAscIdAsc(campaign.getId()).stream()
                .map(asset -> new CampaignAssetView(asset.getId(), asset.getAssetType().name(),
                        asset.getUrl(), asset.getAltText(), asset.getDisplayOrder()))
                .toList();
    }

    /** 内容公开读与渲染同口径：只读已发布专场，未知或未发布一律按不存在处理 */
    private Campaign requirePublished(String campaignCode) {
        Campaign campaign = campaigns.findByCampaignCode(campaignCode)
                .orElseThrow(() -> new CampaignNotFoundException(campaignCode));
        if (campaign.getStatus() != CampaignStatus.PUBLISHED) {
            throw new CampaignNotFoundException(campaignCode);
        }
        return campaign;
    }

    @Override
    @Transactional
    public CampaignView updateDraft(String campaignCode, UpdateCampaignCommand command) {
        Campaign campaign = campaigns.findByCampaignCode(campaignCode)
                .orElseThrow(() -> new CampaignNotFoundException(campaignCode));

        if (command.title() != null) {
            campaign.setTitle(command.title());
        }
        if (command.subtitle() != null) {
            campaign.setSubtitle(command.subtitle());
        }

        if (command.config() != null) {
            configValidator.validate(command.config());
            try {
                campaign.setConfigSnapshot(objectMapper.writeValueAsString(command.config()));
            } catch (Exception e) {
                throw new CampaignConfigValidationException("failed to serialize config: " + e.getMessage());
            }
        }

        return toView(campaigns.save(campaign));
    }

    @Override
    @Transactional
    public void validateCampaign(String campaignCode) {
        Campaign campaign = campaigns.findByCampaignCode(campaignCode)
                .orElseThrow(() -> new CampaignNotFoundException(campaignCode));

        if (campaign.getConfigSnapshot() != null) {
            configValidator.validateJson(campaign.getConfigSnapshot());
        }

        campaign.markValidated();
        campaigns.save(campaign);
    }

    @Override
    @Transactional
    public CampaignRevisionView publish(String campaignCode) {
        Campaign campaign = campaigns.findByCampaignCode(campaignCode)
                .orElseThrow(() -> new CampaignNotFoundException(campaignCode));

        if (campaign.getStatus() != CampaignStatus.VALIDATED) {
            throw new IllegalStateException("campaign must be validated before publishing: " + campaignCode);
        }

        if (campaign.getConfigSnapshot() == null) {
            throw new IllegalStateException("campaign has no config to publish: " + campaignCode);
        }

        configValidator.validateJson(campaign.getConfigSnapshot());

        int nextRevision = revisions.countByCampaignId(campaign.getId()) + 1;
        CampaignRevision revision = new CampaignRevision(campaign.getId(), nextRevision, campaign.getConfigSnapshot());
        revision = revisions.save(revision);

        campaign.publish(revision.getId());
        campaigns.save(campaign);

        return toRevisionView(revision);
    }

    @Override
    @Transactional
    public void unpublish(String campaignCode) {
        Campaign campaign = campaigns.findByCampaignCode(campaignCode)
                .orElseThrow(() -> new CampaignNotFoundException(campaignCode));
        campaign.unpublish();
        campaigns.save(campaign);
    }

    @Override
    @Transactional
    public void archive(String campaignCode) {
        Campaign campaign = campaigns.findByCampaignCode(campaignCode)
                .orElseThrow(() -> new CampaignNotFoundException(campaignCode));
        campaign.archive();
        campaigns.save(campaign);
    }

    @Override
    @Transactional(readOnly = true)
    public CampaignRevisionView publishedRevision(String campaignCode) {
        Campaign campaign = campaigns.findByCampaignCode(campaignCode)
                .orElseThrow(() -> new CampaignNotFoundException(campaignCode));

        if (campaign.getCurrentRevisionId() == null) {
            throw new IllegalStateException("campaign has no published revision: " + campaignCode);
        }

        return revisions.findById(campaign.getCurrentRevisionId())
                .map(this::toRevisionView)
                .orElseThrow(() -> new CampaignRevisionNotFoundException(campaign.getCurrentRevisionId()));
    }

    @Override
    @Transactional(readOnly = true)
    public RenderedPageView renderPublished(String campaignCode) {
        CampaignRevisionView revision = publishedRevision(campaignCode);
        return renderPage(revision.revisionNumber(), revision.configSnapshot());
    }

    private RenderedPageView renderPage(Integer revisionNumber, String configJson) {
        try {
            CampaignConfig config = objectMapper.readValue(configJson, CampaignConfig.class);
            var page = renderer.render(config);
            List<RenderedSectionView> sections = page.sections.stream()
                    .map(s -> new RenderedSectionView(s.id, s.type, s.props, s.content))
                    .toList();
            return new RenderedPageView(page.schemaVersion, page.template,
                    page.seo != null ? page.seo.title : null,
                    page.seo != null ? page.seo.description : null,
                    sections);
        } catch (CampaignConfigValidationException | IllegalStateException | IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("invalid published config: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<CampaignRevisionView> revisions(String campaignCode) {
        Campaign campaign = campaigns.findByCampaignCode(campaignCode)
                .orElseThrow(() -> new CampaignNotFoundException(campaignCode));

        return revisions.findByCampaignIdOrderByRevisionNumberDesc(campaign.getId()).stream()
                .map(this::toRevisionView)
                .toList();
    }

    @Override
    @Transactional
    public void addEvent(String campaignCode, AddEventCommand command) {
        Campaign campaign = campaigns.findByCampaignCode(campaignCode)
                .orElseThrow(() -> new CampaignNotFoundException(campaignCode));

        // 引用存在性由 campaign_events 的外键约束保证（FK → event.id）；
        // 专场只按 ID 引用活动，不读取活动模块内部状态。
        CampaignEvent campaignEvent = new CampaignEvent(campaign.getId(), command.eventId(), command.displayOrder());
        campaignEvents.save(campaignEvent);
    }

    @Override
    @Transactional
    public void removeEvent(String campaignCode, Long eventId) {
        Campaign campaign = campaigns.findByCampaignCode(campaignCode)
                .orElseThrow(() -> new CampaignNotFoundException(campaignCode));

        campaignEvents.deleteByCampaignIdAndEventId(campaign.getId(), eventId);
    }

    @Override
    @Transactional
    public void addProduct(String campaignCode, AddProductCommand command) {
        Campaign campaign = campaigns.findByCampaignCode(campaignCode)
                .orElseThrow(() -> new CampaignNotFoundException(campaignCode));

        // 引用存在性由 campaign_products 的外键约束保证（FK → product.id）；
        // 专场只按 ID 引用商品，不复制商品也不虚构库存。
        CampaignProduct campaignProduct = new CampaignProduct(campaign.getId(), command.productId(), command.displayOrder());
        campaignProducts.save(campaignProduct);
    }

    @Override
    @Transactional
    public void removeProduct(String campaignCode, Long productId) {
        Campaign campaign = campaigns.findByCampaignCode(campaignCode)
                .orElseThrow(() -> new CampaignNotFoundException(campaignCode));

        campaignProducts.deleteByCampaignIdAndProductId(campaign.getId(), productId);
    }

    @Override
    @Transactional
    public CampaignAssetView addAsset(String campaignCode, AddAssetCommand command) {
        Campaign campaign = campaigns.findByCampaignCode(campaignCode)
                .orElseThrow(() -> new CampaignNotFoundException(campaignCode));

        CampaignAsset.AssetType assetType = CampaignAsset.AssetType.valueOf(command.assetType());
        CampaignAsset asset = new CampaignAsset(campaign.getId(), assetType, requireText(command.url(), "url"),
                command.altText(), order(command.displayOrder()));
        CampaignAsset saved = campaignAssets.save(asset);
        return new CampaignAssetView(saved.getId(), saved.getAssetType().name(), saved.getUrl(),
                saved.getAltText(), saved.getDisplayOrder());
    }

    @Override
    @Transactional
    public void removeAsset(String campaignCode, Long assetId) {
        Campaign campaign = campaigns.findByCampaignCode(campaignCode)
                .orElseThrow(() -> new CampaignNotFoundException(campaignCode));

        campaignAssets.deleteByCampaignIdAndId(campaign.getId(), assetId);
    }

    @Override
    @Transactional
    public CampaignRuleView addRule(String campaignCode, AddRuleCommand command) {
        Campaign campaign = campaigns.findByCampaignCode(campaignCode)
                .orElseThrow(() -> new CampaignNotFoundException(campaignCode));

        CampaignRuleDocument rule = new CampaignRuleDocument(campaign.getId(),
                requireText(command.title(), "title"), requireText(command.content(), "content"),
                order(command.displayOrder()));
        CampaignRuleDocument saved = campaignRules.save(rule);
        return new CampaignRuleView(saved.getId(), saved.getTitle(), saved.getContent());
    }

    @Override
    @Transactional
    public void removeRule(String campaignCode, Long ruleId) {
        Campaign campaign = campaigns.findByCampaignCode(campaignCode)
                .orElseThrow(() -> new CampaignNotFoundException(campaignCode));

        campaignRules.deleteByCampaignIdAndId(campaign.getId(), ruleId);
    }

    @Override
    @Transactional
    public CampaignFaqView addFaq(String campaignCode, AddFaqCommand command) {
        Campaign campaign = campaigns.findByCampaignCode(campaignCode)
                .orElseThrow(() -> new CampaignNotFoundException(campaignCode));

        CampaignFaq faq = new CampaignFaq(campaign.getId(),
                requireText(command.question(), "question"), requireText(command.answer(), "answer"),
                order(command.displayOrder()));
        CampaignFaq saved = campaignFaqs.save(faq);
        return new CampaignFaqView(saved.getId(), saved.getQuestion(), saved.getAnswer());
    }

    @Override
    @Transactional
    public void removeFaq(String campaignCode, Long faqId) {
        Campaign campaign = campaigns.findByCampaignCode(campaignCode)
                .orElseThrow(() -> new CampaignNotFoundException(campaignCode));

        campaignFaqs.deleteByCampaignIdAndId(campaign.getId(), faqId);
    }

    /** 内容字段非空校验：空串与纯空白都不算内容，避免公开面出现空条目 */
    private String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    /** 排序缺省为 0（按录入顺序追加在末尾） */
    private int order(Integer displayOrder) {
        return displayOrder == null ? 0 : displayOrder;
    }

    private CampaignView toView(Campaign c) {
        return CampaignView.of(c.getId(), c.getCampaignCode(), c.getTitle(), c.getSubtitle(),
                c.getStatus().name(), c.getCurrentRevisionId());
    }

    private CampaignRevisionView toRevisionView(CampaignRevision r) {
        return CampaignRevisionView.of(r.getId(), r.getCampaignId(), r.getRevisionNumber(), r.getConfigSnapshot());
    }
}
