package site.awn.fulfillment.application;

import org.springframework.stereotype.Component;
import site.awn.fulfillment.domain.Fulfillment;
import site.awn.fulfillment.infrastructure.FulfillmentRepository;
import site.awn.shared.outbox.OutboxEventHandler;
import site.awn.shared.outbox.OutboxMessage;
import site.awn.shared.outbox.OutboxMessageRepository;
import site.awn.shared.outbox.OutboxPayloads;

import java.util.UUID;

/**
 * 支付成功事件消费者（事件触发履约）：PAYMENT_SUCCEEDED → 履约单 PENDING_SELLER。
 * 支付成功不自动等于交付成功：完成还需卖家交付 + 平台核验。
 * 一单一履约（order_no 唯一），事件重放时不重复建单、不重复发 FULFILLMENT_STARTED。
 * FULFILLMENT_STARTED 同事务写 Outbox：订单模块据此将 PAID → FULFILLING。
 */
@Component
public class PaymentSucceededFulfillmentHandler implements OutboxEventHandler {

    public static final String CONSUMER = "fulfillment";
    private static final String AGGREGATE_TYPE = "fulfillment";

    private final FulfillmentRepository fulfillments;
    private final OutboxMessageRepository outbox;

    public PaymentSucceededFulfillmentHandler(FulfillmentRepository fulfillments,
                                              OutboxMessageRepository outbox) {
        this.fulfillments = fulfillments;
        this.outbox = outbox;
    }

    @Override
    public String consumer() {
        return CONSUMER;
    }

    @Override
    public boolean supports(String eventType) {
        return "PAYMENT_SUCCEEDED".equals(eventType);
    }

    @Override
    public void handle(OutboxMessage message) {
        String orderNo = OutboxPayloads.string(message.getPayload(), "orderNo");
        if (orderNo == null) {
            throw new IllegalArgumentException("PAYMENT_SUCCEEDED payload missing orderNo");
        }
        // 一单一履约：事件重放 / inbox 去重兜底之外的幂等保护
        if (fulfillments.findByOrderNo(orderNo).isPresent()) {
            return;
        }
        Fulfillment created = new Fulfillment("FUL-" + UUID.randomUUID(), orderNo);
        fulfillments.save(created);
        outbox.insertPending(AGGREGATE_TYPE, created.getFulfillmentNo(), "FULFILLMENT_STARTED",
                "{\"fulfillmentNo\":\"" + created.getFulfillmentNo()
                        + "\",\"orderNo\":\"" + created.getOrderNo()
                        + "\",\"status\":\"PENDING_SELLER\"}");
    }
}
