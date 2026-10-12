package site.awn.campaign.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import site.awn.campaign.domain.CampaignAsset;

import java.util.List;

public interface CampaignAssetRepository extends JpaRepository<CampaignAsset, Long> {
    List<CampaignAsset> findByCampaignIdOrderByDisplayOrderAsc(Long campaignId);
    void deleteByCampaignId(Long campaignId);
}
