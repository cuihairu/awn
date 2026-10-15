package site.awn.order.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import site.awn.order.domain.DisputeStatus;
import site.awn.order.domain.OrderDispute;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface OrderDisputeRepository extends JpaRepository<OrderDispute, Long> {

    Optional<OrderDispute> findByDisputeNo(String disputeNo);

    List<OrderDispute> findByOrderNoOrderByCreatedAtDesc(String orderNo);

    boolean existsByOrderNoAndStatusIn(String orderNo, Set<DisputeStatus> statuses);

    /** 运营后台争议视图：最近争议（新→旧，限 50 条） */
    List<OrderDispute> findTop50ByOrderByIdDesc();

    /** 卖家面争议列表：按开单时快照的挂牌卖家归属（新→旧，限 50 条） */
    List<OrderDispute> findTop50BySellerIdOrderByIdDesc(Long sellerId);
}
