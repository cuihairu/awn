package site.awn.campaign.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import site.awn.campaign.domain.Campaign;

import java.util.Optional;

public interface CampaignRepository extends JpaRepository<Campaign, Long> {
    Optional<Campaign> findByCampaignCode(String campaignCode);
}
