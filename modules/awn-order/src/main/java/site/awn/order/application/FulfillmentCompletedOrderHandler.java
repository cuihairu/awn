package site.awn.order.application;

import org.springframework.stereotype.Component;
import site.awn.order.domain.OrderConflictException;
import site.awn.shared.outbox.OutboxEventHandler;
import site.awn.shared.outbox.OutboxMessage;
import site.awn.shared.outbox.OutboxPayloads;

/**
 * 交付完成事件消费者（事件闭环：FULFILLMENT_COMPLETED → 订单 FULFILLED）。
 * 仅平台核验通过触发；卖家交付 / 核验拒绝不走此事件。
 */
@Component
public class FulfillmentCompletedOrderHandler implements OutboxEventHandler {

    public static final String CONSUMER = "order";

    private final OrderWriteService orders;

    public FulfillmentCompletedOrderHandler(OrderWriteService orders) {
        this.orders = orders;
    }

    @Override
    public String consumer() {
        return CONSUMER;
    }

    @Override
    public boolean supports(String eventType) {
        return "FULFILLMENT_COMPLETED".equals(eventType);
    }

    @Override
    public void handle(OutboxMessage message) {
        String orderNo = OutboxPayloads.string(message.getPayload(), "orderNo");
        if (orderNo == null) {
            throw new IllegalArgumentException("FULFILLMENT_COMPLETED payload missing orderNo");
        }
        try {
            orders.markFulfilled(orderNo);
        } catch (OrderConflictException e) {
            throw new IllegalStateException("order conflict while applying fulfillment completion: " + e.getMessage(), e);
        }
    }
}
