package site.awn.campaign.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import site.awn.campaign.domain.CampaignFaq;

import java.util.List;

public interface CampaignFaqRepository extends JpaRepository<CampaignFaq, Long> {
    /** 公开读与运营配置同口径：按录入顺序取（同序按 id 稳定） */
    List<CampaignFaq> findByCampaignIdOrderByDisplayOrderAscIdAsc(Long campaignId);

    /** 删除只认归属专场，避免按 id 删掉他专场的问答 */
    void deleteByCampaignIdAndId(Long campaignId, Long id);
}