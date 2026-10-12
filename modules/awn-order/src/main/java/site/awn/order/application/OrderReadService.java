package site.awn.order.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.awn.order.api.OrderItemView;
import site.awn.order.api.OrderView;
import site.awn.order.domain.Order;
import site.awn.order.domain.OrderItem;
import site.awn.order.domain.OrderNotFoundException;
import site.awn.order.infrastructure.OrderItemRepository;
import site.awn.order.infrastructure.OrderRepository;

import java.util.List;

@Service
public class OrderReadService {

    private final OrderRepository orders;
    private final OrderItemRepository items;

    OrderReadService(OrderRepository orders, OrderItemRepository items) {
        this.orders = orders;
        this.items = items;
    }

    @Transactional(readOnly = true)
    public OrderView orderOf(String orderNo) {
        Order order = orders.findByOrderNo(orderNo).orElseThrow(() -> new OrderNotFoundException(orderNo));
        return toView(order, items.findByOrderId(order.getId()));
    }

    @Transactional(readOnly = true)
    public java.util.List<OrderView> recentOrders() {
        return orders.findTop50ByOrderByIdDesc().stream()
                .map(o -> toView(o, items.findByOrderId(o.getId())))
                .toList();
    }

    static OrderView toView(Order order, List<OrderItem> lines) {
        List<OrderItemView> itemViews = lines.stream()
                .map(i -> new OrderItemView(
                        i.getPoolCode(), i.getProductId(), i.getProductNameSnapshot(),
                        i.getSessionId(), i.getTicketCategorySnapshot(),
                        i.getQuantity(), i.getUnitPrice(), i.getDiscountAmount(), i.getFeeAmount(),
                        i.getLineTotal(), i.getReservationNo()))
                .toList();
        return new OrderView(order.getOrderNo(), order.getStatus(), order.getCurrency(),
                order.getTotalAmount(), order.getDiscountAmount(), order.getFeeAmount(),
                order.getPayAmount(), order.getExpiresAt(), itemViews);
    }
}
