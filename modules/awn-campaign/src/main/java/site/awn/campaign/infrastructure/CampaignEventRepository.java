package site.awn.campaign.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import site.awn.campaign.domain.CampaignEvent;

import java.util.List;

public interface CampaignEventRepository extends JpaRepository<CampaignEvent, Long> {
    List<CampaignEvent> findByCampaignIdOrderByDisplayOrderAsc(Long campaignId);
    void deleteByCampaignId(Long campaignId);
    void deleteByCampaignIdAndEventId(Long campaignId, Long eventId);
}
