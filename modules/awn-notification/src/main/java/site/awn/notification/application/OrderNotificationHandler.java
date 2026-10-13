package site.awn.notification.application;

import org.springframework.stereotype.Component;
import site.awn.notification.api.NotificationFacade;
import site.awn.notification.api.SendNotificationCommand;
import site.awn.shared.outbox.OutboxEventHandler;
import site.awn.shared.outbox.OutboxMessage;
import site.awn.shared.outbox.OutboxPayloads;

/**
 * 订单事件消费者（事件闭环：ORDER_CREATED / ORDER_PAID → 买家站内信）。
 * 消费发生在订单事务提交之后（Outbox 模式），且 send 对通道失败只记 FAILED 不抛——
 * 通知失败不回滚、不阻塞订单流程。
 */
@Component
public class OrderNotificationHandler implements OutboxEventHandler {

    public static final String CONSUMER = "notification";

    private final NotificationFacade notifications;

    public OrderNotificationHandler(NotificationFacade notifications) {
        this.notifications = notifications;
    }

    @Override
    public String consumer() {
        return CONSUMER;
    }

    @Override
    public boolean supports(String eventType) {
        return "ORDER_CREATED".equals(eventType) || "ORDER_PAID".equals(eventType);
    }

    @Override
    public void handle(OutboxMessage message) {
        String orderNo = OutboxPayloads.string(message.getPayload(), "orderNo");
        Long userId = OutboxPayloads.longValue(message.getPayload(), "userId");
        if (orderNo == null || userId == null) {
            throw new IllegalArgumentException(message.getEventType() + " payload missing orderNo/userId");
        }
        notifications.send(new SendNotificationCommand(
                userId, "INBOX", message.getEventType(),
                title(message.getEventType()), body(message.getEventType(), orderNo), orderNo));
    }

    private static String title(String eventType) {
        return "ORDER_PAID".equals(eventType) ? "支付成功" : "下单成功";
    }

    private static String body(String eventType, String orderNo) {
        return "ORDER_PAID".equals(eventType)
                ? "订单 " + orderNo + " 已支付成功，等待出票"
                : "订单 " + orderNo + " 已创建，请在支付截止时间内完成支付";
    }
}
