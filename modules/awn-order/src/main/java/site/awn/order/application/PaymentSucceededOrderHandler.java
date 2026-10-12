package site.awn.order.application;

import org.springframework.stereotype.Component;
import site.awn.order.domain.OrderConflictException;
import site.awn.shared.outbox.OutboxEventHandler;
import site.awn.shared.outbox.OutboxMessage;
import site.awn.shared.outbox.OutboxPayloads;

/**
 * 支付成功事件消费者（事件闭环：PAYMENT_SUCCEEDED → 订单 PAID）。
 * 与超时释放竞态时 markPaid 守卫裁决：订单已 EXPIRED 抛 OrderConflictException，
 * 消息按退避重试后转 FAILED 留痕，进入异常订单处理流程而非盲目确认。
 */
@Component
public class PaymentSucceededOrderHandler implements OutboxEventHandler {

    public static final String CONSUMER = "order";

    private final OrderWriteService orders;

    public PaymentSucceededOrderHandler(OrderWriteService orders) {
        this.orders = orders;
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
        try {
            orders.markPaid(orderNo);
        } catch (OrderConflictException e) {
            // 与超时释放竞态：不吞异常，让消息重试后转 FAILED 留痕（异常订单处理流程入口）
            throw new IllegalStateException("order conflict while applying payment: " + e.getMessage(), e);
        }
    }
}
