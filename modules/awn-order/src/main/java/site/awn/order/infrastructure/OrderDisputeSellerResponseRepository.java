package site.awn.order.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import site.awn.order.domain.DisputeSellerResponse;

import java.util.List;

/** 卖家申辩按争议读取（管理面裁决依据，提交时间正序） */
public interface OrderDisputeSellerResponseRepository
        extends JpaRepository<DisputeSellerResponse, Long> {

    List<DisputeSellerResponse> findByDisputeNoOrderByCreatedAtAsc(String disputeNo);
}
