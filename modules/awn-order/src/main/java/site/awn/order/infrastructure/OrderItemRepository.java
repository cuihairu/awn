package site.awn.order.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import site.awn.order.domain.OrderItem;

import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    List<OrderItem> findByOrderId(Long orderId);
}
