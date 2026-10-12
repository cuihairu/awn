package site.awn.fulfillment.application;

import org.springframework.stereotype.Component;
import site.awn.fulfillment.domain.Fulfillment;
import site.awn.fulfillment.infrastructure.FulfillmentRepository;
import site.awn.shared.outbox.OutboxEventHandler;
import site.awn.shared.outbox.OutboxMessage;
import site.awn.shared.outbox.OutboxPayloads;

import java.util.UUID;

/**
 * 支付成功事件消费者（事件触发履约）：PAYMENT_SUCCEEDED → 履约单 PENDING_SELLER。
 * 支付成功不自动等于交付成功：完成还需卖家交付 + 平台核验。
 * 幂等：一单一履约（order_no 唯一），事件重放时返回已有履约单。
 */
@Component
public class PaymentSucceededFulfillmentHandler implements OutboxEventHandler {

    public static final String CONSUMER = "fulfillment";

    private final FulfillmentRepository fulfillments;

    public PaymentSucceededFulfillmentHandler(FulfillmentRepository fulfillments) {
        this.fulfillments = fulfillments;
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
        fulfillments.save(new Fulfillment("FUL-" + UUID.randomUUID(), orderNo));
    }
}
