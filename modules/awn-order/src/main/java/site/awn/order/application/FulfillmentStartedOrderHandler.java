package site.awn.order.application;

import org.springframework.stereotype.Component;
import site.awn.order.domain.OrderConflictException;
import site.awn.shared.outbox.OutboxEventHandler;
import site.awn.shared.outbox.OutboxMessage;
import site.awn.shared.outbox.OutboxPayloads;

/**
 * 履约开始事件消费者（事件闭环：FULFILLMENT_STARTED → 订单 FULFILLING）。
 * 履约单创建（PENDING_SELLER）即视为履约开始；交付完成另行驱动 FULFILLED。
 */
@Component
public class FulfillmentStartedOrderHandler implements OutboxEventHandler {

    public static final String CONSUMER = "order";

    private final OrderWriteService orders;

    public FulfillmentStartedOrderHandler(OrderWriteService orders) {
        this.orders = orders;
    }

    @Override
    public String consumer() {
        return CONSUMER;
    }

    @Override
    public boolean supports(String eventType) {
        return "FULFILLMENT_STARTED".equals(eventType);
    }

    @Override
    public void handle(OutboxMessage message) {
        String orderNo = OutboxPayloads.string(message.getPayload(), "orderNo");
        if (orderNo == null) {
            throw new IllegalArgumentException("FULFILLMENT_STARTED payload missing orderNo");
        }
        try {
            orders.markFulfilling(orderNo);
        } catch (OrderConflictException e) {
            throw new IllegalStateException("order conflict while applying fulfillment start: " + e.getMessage(), e);
        }
    }
}
