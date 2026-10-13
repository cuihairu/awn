package site.awn.notification;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import site.awn.inventory.api.CreatePoolCommand;
import site.awn.inventory.api.InventoryFacade;
import site.awn.notification.api.NotificationFacade;
import site.awn.notification.api.NotificationView;
import site.awn.notification.api.SendNotificationCommand;
import site.awn.order.api.CreateOrderCommand;
import site.awn.order.api.OrderFacade;
import site.awn.order.api.OrderLineCommand;
import site.awn.order.api.OrderView;
import site.awn.order.domain.OrderStatus;
import site.awn.payment.api.CreatePaymentCommand;
import site.awn.payment.api.PaymentCallbackCommand;
import site.awn.payment.api.PaymentFacade;
import site.awn.payment.api.PaymentView;
import site.awn.payment.sandbox.SandboxPaymentGateway;
import site.awn.shared.outbox.OutboxDispatchService;
import site.awn.support.AbstractPostgresIntegrationTest;
import site.awn.support.CatalogProductFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * notification 模块：站内信通道适配 + 失败不回滚订单。
 * 验收口径（module-boundaries.md / async-outbox.md）：
 * - 至少一种真实通道适配（站内信落库、可查收件箱）；
 * - 渠道失败记录 FAILED 返回，绝不向上抛——通知失败不回滚已成功的订单；
 * - 下单 / 支付成功事件驱动站内信（事件闭环）。
 */
@SpringBootTest
class NotificationModuleTests extends AbstractPostgresIntegrationTest {

    @Autowired NotificationFacade notifications;
    @Autowired OrderFacade orderFacade;
    @Autowired InventoryFacade inventory;
    @Autowired PaymentFacade payments;
    @Autowired SandboxPaymentGateway sandbox;
    @Autowired OutboxDispatchService dispatcher;
    @Autowired CatalogProductFactory products;

    @Test
    void inboxChannelDeliversPersistedMessage() {
        NotificationView sent = notifications.send(new SendNotificationCommand(
                42L, "INBOX", "ORDER_PAID", "支付成功", "订单支付成功", "ORD-inbox"));

        assertThat(sent.status()).isEqualTo("SENT");
        assertThat(notifications.inboxOf(42L))
                .anyMatch(m -> m.reference().equals("ORD-inbox") && m.channel().equals("INBOX"));
    }

    @Test
    void unconnectedChannelIsRecordedNotPropagated() {
        // 邮件 / 短信通道未接入：记录 FAILED，不抛异常
        NotificationView failed = notifications.send(new SendNotificationCommand(
                42L, "EMAIL", "ORDER_PAID", "支付成功", "订单支付成功", "ORD-email"));

        assertThat(failed.status()).as("未接入通道必须记录失败而不是抛出").isEqualTo("FAILED");
        assertThat(failed.failReason()).contains("not connected");
        assertThat(notifications.inboxOf(42L)).noneMatch(m -> m.reference().equals("ORD-email"));
    }

    @Test
    void unknownChannelIsRecordedNotPropagated() {
        assertThatCode(() -> notifications.send(new SendNotificationCommand(
                42L, "PIGEON", "ORDER_PAID", "x", "y", "ORD-pigeon")))
                .doesNotThrowAnyException();
        assertThat(notifications.inboxOf(42L)).noneMatch(m -> m.reference().equals("ORD-pigeon"));
    }

    @Test
    void orderEventsDeliverInboxMessagesAndFailureLeavesOrderIntact() {
        String suffix = UUID.randomUUID().toString().substring(0, 12);
        String poolCode = "pool-" + suffix;
        inventory.createPool(new CreatePoolCommand(poolCode, products.productCode(suffix), "sess-" + suffix, 10));
        var line = new OrderLineCommand(poolCode, "prod-" + suffix, "通知测试演出",
                "sess-" + suffix, null, 1, 300, 0, 0);
        OrderView order = orderFacade.createOrder(
                new CreateOrderCommand("ntf-" + suffix, 4242L, "CNY", List.of(line)));

        // 事件闭环：ORDER_CREATED → 下单成功站内信
        drainOutbox();
        assertThat(notifications.inboxOf(4242L))
                .anyMatch(m -> m.reference().equals(order.orderNo()) && m.eventType().equals("ORDER_CREATED"));

        PaymentView payment = payments.createPayment(
                new CreatePaymentCommand(order.orderNo(), "sandbox", "CNY", order.payAmount()));
        sandbox.succeedCharge(payment.channelTxnNo());
        payments.handleCallback(success(payment));
        drainOutbox();

        // 支付成功站内信已投递；订单已进入履约中
        assertThat(notifications.inboxOf(4242L))
                .anyMatch(m -> m.reference().equals(order.orderNo()) && m.eventType().equals("ORDER_PAID"));
        assertThat(orderFacade.orderOf(order.orderNo()).status()).isEqualTo(OrderStatus.FULFILLING);

        // 通知失败（未接入通道）不影响已提交的订单与支付
        notifications.send(new SendNotificationCommand(
                4242L, "EMAIL", "ORDER_PAID", "支付成功", "订单支付成功", order.orderNo()));
        assertThat(orderFacade.orderOf(order.orderNo()).status()).isEqualTo(OrderStatus.FULFILLING);
    }

    // ------------------------------------------------------------------

    private PaymentCallbackCommand success(PaymentView payment) {
        return new PaymentCallbackCommand("sandbox", "{}",
                Map.of(SandboxPaymentGateway.SIGNATURE_HEADER, "awn-sandbox"),
                payment.channelTxnNo(), payment.currency(), payment.amount(), true);
    }

    /** 等价 Worker 连续分发直到本轮无到期消息 */
    private void drainOutbox() {
        for (int i = 0; i < 60 && dispatcher.dispatchDue(Instant.now()) > 0; i++) { }
    }
}
