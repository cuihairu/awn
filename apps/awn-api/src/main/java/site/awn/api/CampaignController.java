package site.awn.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import site.awn.campaign.api.CampaignFacade;
import site.awn.campaign.api.CampaignRevisionView;
import site.awn.campaign.api.CampaignView;
import site.awn.campaign.api.CreateCampaignCommand;
import site.awn.campaign.api.RenderedPageView;
import site.awn.campaign.api.UpdateCampaignCommand;

/**
 * 专场 API：草稿、校验、发布、已发布版本读取与渲染。
 * 渲染只读取已发布版本（不可变 revision），草稿编辑不影响线上页面。
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
}
