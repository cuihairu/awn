package site.awn.campaign.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import site.awn.campaign.domain.CampaignRevision;

import java.util.List;
import java.util.Optional;

public interface CampaignRevisionRepository extends JpaRepository<CampaignRevision, Long> {
    Optional<CampaignRevision> findByCampaignIdAndRevisionNumber(Long campaignId, Integer revisionNumber);
    List<CampaignRevision> findByCampaignIdOrderByRevisionNumberDesc(Long campaignId);
    int countByCampaignId(Long campaignId);
}
