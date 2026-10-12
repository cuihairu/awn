package site.awn.order.application;

import org.springframework.stereotype.Component;
import site.awn.order.domain.OrderConflictException;
import site.awn.shared.outbox.OutboxEventHandler;
import site.awn.shared.outbox.OutboxMessage;
import site.awn.shared.outbox.OutboxPayloads;

/**
 * 履约失败事件消费者（异常流程闭环：FULFILLMENT_FAILED → 订单 REFUND_PENDING）。
 * 履约失败进入退款流程而非静默吞掉；非法迁移（如订单已退款）留痕重试后 FAILED。
 */
@Component
public class FulfillmentFailedOrderHandler implements OutboxEventHandler {

    public static final String CONSUMER = "order";

    private final OrderWriteService orders;

    public FulfillmentFailedOrderHandler(OrderWriteService orders) {
        this.orders = orders;
    }

    @Override
    public String consumer() {
        return CONSUMER;
    }

    @Override
    public boolean supports(String eventType) {
        return "FULFILLMENT_FAILED".equals(eventType);
    }

    @Override
    public void handle(OutboxMessage message) {
        String orderNo = OutboxPayloads.string(message.getPayload(), "orderNo");
        if (orderNo == null) {
            throw new IllegalArgumentException("FULFILLMENT_FAILED payload missing orderNo");
        }
        try {
            orders.markRefundPending(orderNo);
        } catch (OrderConflictException e) {
            throw new IllegalStateException("order conflict while applying fulfillment failure: " + e.getMessage(), e);
        }
    }
}
