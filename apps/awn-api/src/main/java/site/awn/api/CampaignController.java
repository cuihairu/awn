package site.awn.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import site.awn.campaign.api.AddAssetCommand;
import site.awn.campaign.api.AddFaqCommand;
import site.awn.campaign.api.AddRuleCommand;
import site.awn.campaign.api.CampaignAssetView;
import site.awn.campaign.api.CampaignFacade;
import site.awn.campaign.api.CampaignFaqView;
import site.awn.campaign.api.CampaignRevisionView;
import site.awn.campaign.api.CampaignRuleView;
import site.awn.campaign.api.CampaignView;
import site.awn.campaign.api.CreateCampaignCommand;
import site.awn.campaign.api.RenderedPageView;
import site.awn.campaign.api.UpdateCampaignCommand;

import java.util.List;

/**
 * 专场 API：草稿、校验、发布、已发布版本读取与渲染，外加须知 / 常见问题 / 素材内容端点。
 * 渲染与内容公开读只读已发布专场，未发布与未知一律 404（草稿内容不泄露）。
 * 写操作（草稿、发布、内容录入）与运营接口同权，见 AdminAuthFilter。
 */
@RestController
@RequestMapping("/api/v1/campaigns")
public class CampaignController {

    private final CampaignFacade campaigns;

    public CampaignController(CampaignFacade campaigns) {
        this.campaigns = campaigns;
    }

    @PostMapping
    public CampaignView create(@RequestBody CreateCampaignCommand command) {
        return campaigns.createCampaign(command);
    }

    @PostMapping("/{campaignCode}/draft")
    public CampaignView updateDraft(@PathVariable String campaignCode,
                                    @RequestBody UpdateCampaignCommand command) {
        return campaigns.updateDraft(campaignCode, command);
    }

    @PostMapping("/{campaignCode}/validate")
    public CampaignView validate(@PathVariable String campaignCode) {
        campaigns.validateCampaign(campaignCode);
        return campaigns.campaign(campaignCode);
    }

    @PostMapping("/{campaignCode}/publish")
    public CampaignRevisionView publish(@PathVariable String campaignCode) {
        return campaigns.publish(campaignCode);
    }

    @PostMapping("/{campaignCode}/unpublish")
    public CampaignView unpublish(@PathVariable String campaignCode) {
        campaigns.unpublish(campaignCode);
        return campaigns.campaign(campaignCode);
    }

    @GetMapping
    public List<CampaignView> publicCampaigns() {
        return campaigns.publicCampaigns();
    }

    @GetMapping("/{campaignCode}")
    public CampaignView campaign(@PathVariable String campaignCode) {
        return campaigns.campaign(campaignCode);
    }

    @GetMapping("/{campaignCode}/published")
    public CampaignRevisionView publishedRevision(@PathVariable String campaignCode) {
        return campaigns.publishedRevision(campaignCode);
    }

    @GetMapping("/{campaignCode}/render")
    public RenderedPageView render(@PathVariable String campaignCode) {
        return campaigns.renderPublished(campaignCode);
    }

    @GetMapping("/{campaignCode}/rules")
    public List<CampaignRuleView> rules(@PathVariable String campaignCode) {
        return campaigns.publicRules(campaignCode);
    }

    @GetMapping("/{campaignCode}/faq")
    public List<CampaignFaqView> faq(@PathVariable String campaignCode) {
        return campaigns.publicFaq(campaignCode);
    }

    @GetMapping("/{campaignCode}/assets")
    public List<CampaignAssetView> assets(@PathVariable String campaignCode) {
        return campaigns.publicAssets(campaignCode);
    }

    @PostMapping("/{campaignCode}/rules")
    public ResponseEntity<CampaignRuleView> addRule(@PathVariable String campaignCode,
                                                     @RequestBody AddRuleCommand command) {
        CampaignRuleView rule = campaigns.addRule(campaignCode, command);
        return ResponseEntity.status(201)
                .location(java.net.URI.create("/api/v1/campaigns/" + campaignCode + "/rules/" + rule.id()))
                .body(rule);
    }

    @DeleteMapping("/{campaignCode}/rules/{ruleId}")
    public ResponseEntity<Void> removeRule(@PathVariable String campaignCode,
                                           @PathVariable Long ruleId) {
        campaigns.removeRule(campaignCode, ruleId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{campaignCode}/faq")
    public ResponseEntity<CampaignFaqView> addFaq(@PathVariable String campaignCode,
                                                   @RequestBody AddFaqCommand command) {
        CampaignFaqView faq = campaigns.addFaq(campaignCode, command);
        return ResponseEntity.status(201)
                .location(java.net.URI.create("/api/v1/campaigns/" + campaignCode + "/faq/" + faq.id()))
                .body(faq);
    }

    @DeleteMapping("/{campaignCode}/faq/{faqId}")
    public ResponseEntity<Void> removeFaq(@PathVariable String campaignCode,
                                          @PathVariable Long faqId) {
        campaigns.removeFaq(campaignCode, faqId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{campaignCode}/assets")
    public ResponseEntity<CampaignAssetView> addAsset(@PathVariable String campaignCode,
                                                      @RequestBody AddAssetCommand command) {
        CampaignAssetView asset = campaigns.addAsset(campaignCode, command);
        return ResponseEntity.status(201)
                .location(java.net.URI.create("/api/v1/campaigns/" + campaignCode + "/assets/" + asset.id()))
                .body(asset);
    }

    @DeleteMapping("/{campaignCode}/assets/{assetId}")
    public ResponseEntity<Void> removeAsset(@PathVariable String campaignCode,
                                            @PathVariable Long assetId) {
        campaigns.removeAsset(campaignCode, assetId);
        return ResponseEntity.ok().build();
    }
}
