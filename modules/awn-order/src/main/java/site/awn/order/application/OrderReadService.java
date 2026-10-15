package site.awn.order.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.awn.order.api.AttendeeView;
import site.awn.order.api.OrderItemView;
import site.awn.order.api.OrderView;
import site.awn.order.domain.Order;
import site.awn.order.domain.OrderAttendee;
import site.awn.order.domain.OrderItem;
import site.awn.order.domain.OrderNotFoundException;
import site.awn.order.domain.OrderStatus;
import site.awn.order.infrastructure.OrderAttendeeRepository;
import site.awn.order.infrastructure.OrderItemRepository;
import site.awn.order.infrastructure.OrderRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class OrderReadService {

    private final OrderRepository orders;
    private final OrderItemRepository items;
    private final OrderAttendeeRepository attendees;

    OrderReadService(OrderRepository orders, OrderItemRepository items,
                     OrderAttendeeRepository attendees) {
        this.orders = orders;
        this.items = items;
        this.attendees = attendees;
    }

    @Transactional(readOnly = true)
    public OrderView orderOf(String orderNo) {
        Order order = orders.findByOrderNo(orderNo).orElseThrow(() -> new OrderNotFoundException(orderNo));
        List<OrderItem> lines = items.findByOrderId(order.getId());
        return toView(order, lines, attendees.findByItemIdIn(itemIds(lines)));
    }

    @Transactional(readOnly = true)
    public java.util.List<OrderView> recentOrders() {
        return orders.findTop50ByOrderByIdDesc().stream()
                .map(o -> {
                    List<OrderItem> lines = items.findByOrderId(o.getId());
                    return toView(o, lines,
                            attendees.findByItemIdIn(itemIds(lines)));
                })
                .toList();
    }

    static OrderView toView(Order order, List<OrderItem> lines, List<OrderAttendee> attendeeRows) {
        Map<Long, List<OrderAttendee>> byItem = attendeeRows.stream()
                .collect(Collectors.groupingBy(OrderAttendee::getItemId));
        List<OrderItemView> itemViews = lines.stream()
                .map(i -> new OrderItemView(
                        i.getPoolCode(), i.getProductId(), i.getProductNameSnapshot(),
                        i.getSessionId(), i.getTicketCategorySnapshot(),
                        i.getQuantity(), i.getUnitPrice(), i.getDiscountAmount(), i.getFeeAmount(),
                        i.getLineTotal(), i.getReservationNo(), i.getListingNo(), i.getUnitNo(),
                        i.getSeatNo(),
                        byItem.getOrDefault(i.getId(), List.of()).stream()
                                .map(a -> AttendeeView.of(a.getName(), a.getIdNo()))
                                .toList()))
                .toList();
        Instant serverTime = Instant.now();
        long payExpiresInSeconds = 0;
        if (order.getStatus() == OrderStatus.PENDING_PAYMENT && order.getExpiresAt() != null) {
            payExpiresInSeconds = Math.max(0,
                    Duration.between(serverTime, order.getExpiresAt()).toSeconds());
        }
        return new OrderView(order.getOrderNo(), order.getStatus(), order.getCurrency(),
                order.getTotalAmount(), order.getDiscountAmount(), order.getFeeAmount(),
                order.getPayAmount(), order.getExpiresAt(), itemViews,
                serverTime, payExpiresInSeconds);
    }

    private static List<Long> itemIds(List<OrderItem> lines) {
        return lines.stream().map(OrderItem::getId).toList();
    }
}
