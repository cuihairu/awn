package site.awn.campaign.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.awn.campaign.api.AddAssetCommand;
import site.awn.campaign.api.AddEventCommand;
import site.awn.campaign.api.AddProductCommand;
import site.awn.campaign.api.CampaignConfig;
import site.awn.campaign.api.CampaignFacade;
import site.awn.campaign.api.CampaignRevisionView;
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
import site.awn.campaign.domain.CampaignNotFoundException;
import site.awn.campaign.domain.CampaignProduct;
import site.awn.campaign.domain.CampaignRevision;
import site.awn.campaign.domain.CampaignRevisionNotFoundException;
import site.awn.campaign.domain.CampaignStatus;
import site.awn.campaign.infrastructure.CampaignAssetRepository;
import site.awn.campaign.infrastructure.CampaignEventRepository;
import site.awn.campaign.infrastructure.CampaignProductRepository;
import site.awn.campaign.infrastructure.CampaignRepository;
import site.awn.campaign.infrastructure.CampaignRevisionRepository;

import java.util.List;

@Service
public class CampaignFacadeImpl implements CampaignFacade {

    private final CampaignRepository campaigns;
    private final CampaignRevisionRepository revisions;
    private final CampaignEventRepository campaignEvents;
    private final CampaignProductRepository campaignProducts;
    private final CampaignAssetRepository campaignAssets;
    private final CampaignConfigValidator configValidator;
    private final CampaignTemplateRenderer renderer;
    private final ObjectMapper objectMapper;

    public CampaignFacadeImpl(CampaignRepository campaigns,
                              CampaignRevisionRepository revisions,
                              CampaignEventRepository campaignEvents,
                              CampaignProductRepository campaignProducts,
                              CampaignAssetRepository campaignAssets,
                              CampaignConfigValidator configValidator,
                              CampaignTemplateRenderer renderer,
                              ObjectMapper objectMapper) {
        this.campaigns = campaigns;
        this.revisions = revisions;
        this.campaignEvents = campaignEvents;
        this.campaignProducts = campaignProducts;
        this.campaignAssets = campaignAssets;
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
    public void addAsset(String campaignCode, AddAssetCommand command) {
        Campaign campaign = campaigns.findByCampaignCode(campaignCode)
                .orElseThrow(() -> new CampaignNotFoundException(campaignCode));

        CampaignAsset.AssetType assetType = CampaignAsset.AssetType.valueOf(command.assetType());
        CampaignAsset asset = new CampaignAsset(campaign.getId(), assetType, command.url(), command.altText(), command.displayOrder());
        campaignAssets.save(asset);
    }

    @Override
    @Transactional
    public void removeAsset(String campaignCode, Long assetId) {
        campaignAssets.deleteById(assetId);
    }

    private CampaignView toView(Campaign c) {
        return CampaignView.of(c.getId(), c.getCampaignCode(), c.getTitle(), c.getSubtitle(),
                c.getStatus().name(), c.getCurrentRevisionId());
    }

    private CampaignRevisionView toRevisionView(CampaignRevision r) {
        return CampaignRevisionView.of(r.getId(), r.getCampaignId(), r.getRevisionNumber(), r.getConfigSnapshot());
    }
}
