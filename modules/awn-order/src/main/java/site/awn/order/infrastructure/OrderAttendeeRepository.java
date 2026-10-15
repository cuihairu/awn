package site.awn.order.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import site.awn.order.domain.OrderAttendee;

import java.util.Collection;
import java.util.List;

public interface OrderAttendeeRepository extends JpaRepository<OrderAttendee, Long> {

    List<OrderAttendee> findByItemIdIn(Collection<Long> itemIds);

    /** 同场次同证件是否已占用（一票一证，跨订单；session 为 NULL 不参与约束） */
    boolean existsBySessionIdAndIdNo(String sessionId, String idNo);
}
