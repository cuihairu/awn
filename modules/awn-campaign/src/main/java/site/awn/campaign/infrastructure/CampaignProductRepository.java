package site.awn.campaign.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import site.awn.campaign.domain.CampaignProduct;

import java.util.List;

public interface CampaignProductRepository extends JpaRepository<CampaignProduct, Long> {
    List<CampaignProduct> findByCampaignIdOrderByDisplayOrderAsc(Long campaignId);
    void deleteByCampaignId(Long campaignId);
    void deleteByCampaignIdAndProductId(Long campaignId, Long productId);
}
