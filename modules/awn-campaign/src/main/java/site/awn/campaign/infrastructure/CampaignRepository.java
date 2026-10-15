package site.awn.campaign.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import site.awn.campaign.domain.Campaign;
import site.awn.campaign.domain.CampaignStatus;

import java.util.List;
import java.util.Optional;

public interface CampaignRepository extends JpaRepository<Campaign, Long> {
    Optional<Campaign> findByCampaignCode(String campaignCode);

    /** 公开列表：只含已发布专场，按最近变更倒序（同值按 id 稳定） */
    List<Campaign> findByStatusOrderByUpdatedAtDescIdDesc(CampaignStatus status);
}
