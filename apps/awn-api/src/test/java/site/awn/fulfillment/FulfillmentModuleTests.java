package site.awn.fulfillment;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import site.awn.fulfillment.api.DeliverCommand;
import site.awn.fulfillment.api.FulfillmentFacade;
import site.awn.fulfillment.api.FulfillmentView;
import site.awn.fulfillment.api.VerifyDeliveryCommand;
import site.awn.fulfillment.domain.FulfillmentConflictException;
import site.awn.inventory.api.CreatePoolCommand;
import site.awn.inventory.api.InventoryFacade;
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
import site.awn.shared.outbox.OutboxMessageRepository;
import site.awn.support.AbstractPostgresIntegrationTest;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 05：交付流程与异常处理。
 * 验收口径：
 * - 支付成功不自动等于交付成功（履约单停在 PENDING_SELLER，需卖家交付 + 平台核验）；
 * - 交付凭证仅白名单存储域；
 * - 履约失败（核验拒绝 / 异常上报）进入异常流程：FAILED + FULFILLMENT_FAILED 事件 → 订单退款流程，
 *   不静默吞掉。
 */
@SpringBootTest
class FulfillmentModuleTests extends AbstractPostgresIntegrationTest {

    @Autowired FulfillmentFacade fulfillment;
    @Autowired PaymentFacade payments;
    @Autowired OrderFacade orderFacade;
    @Autowired InventoryFacade inventory;
    @Autowired OutboxDispatchService dispatcher;
    @Autowired OutboxMessageRepository outbox;
    @Autowired SandboxPaymentGateway sandbox;
    @Autowired site.awn.support.CatalogProductFactory products;

    @Test
    void paymentSuccessCreatesPendingSellerNotCompleted() {
        paidOrder();
        drainOutbox();

        OrderView order = orderFacade.orderOf(lastOrderNo);
        FulfillmentView view = fulfillment.fulfillmentOf(lastOrderNo);

        // 履约单已建（PENDING_SELLER）→ 订单进入履约中，不再是已支付待履约
        assertThat(order.status()).isEqualTo(OrderStatus.FULFILLING);
        assertThat(view.status())
                .as("支付成功不自动等于交付成功")
                .isEqualTo("PENDING_SELLER");
    }

    @Test
    void orderMovesToFulfilledWhenDeliveryVerified() {
        paidOrder();
        drainOutbox();

        // 卖家交付只是出票，交付完成需平台核验
        fulfillment.deliverSeller(new DeliverCommand(
                lastOrderNo, "https://assets.awn.site/delivery/" + UUID.randomUUID() + ".png", null));
        assertThat(orderFacade.orderOf(lastOrderNo).status())
                .as("卖家交付不等于交付完成").isEqualTo(OrderStatus.FULFILLING);

        fulfillment.verifyDelivery(new VerifyDeliveryCommand(
                lastOrderNo, true, "核验员-3", "凭证有效"));
        drainOutbox();

        assertThat(orderFacade.orderOf(lastOrderNo).status()).isEqualTo(OrderStatus.FULFILLED);
    }

    @Test
    void deliveryAndVerificationCompletesFulfillment() {
        paidOrder();
        drainOutbox();

        FulfillmentView delivered = fulfillment.deliverSeller(new DeliverCommand(
                lastOrderNo, "https://assets.awn.site/delivery/" + UUID.randomUUID() + ".png", "卖家已出票"));
        assertThat(delivered.status()).isEqualTo("PENDING_VERIFICATION");

        FulfillmentView completed = fulfillment.verifyDelivery(new VerifyDeliveryCommand(
                lastOrderNo, true, "核验员-1", "凭证有效"));
        assertThat(completed.status()).isEqualTo("COMPLETED");

        // 终态不可再推进
        assertThatThrownBy(() -> fulfillment.reportFailure(lastOrderNo, "too late"))
                .isInstanceOf(FulfillmentConflictException.class)
                .hasMessageContaining("not allowed");
    }

    @Test
    void nonWhitelistedEvidenceUrlIsRejected() {
        paidOrder();
        drainOutbox();

        assertThatThrownBy(() -> fulfillment.deliverSeller(new DeliverCommand(
                lastOrderNo, "https://evil.example.com/voucher.png", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("whitelisted storage domain");
        assertThatThrownBy(() -> fulfillment.deliverSeller(new DeliverCommand(
                lastOrderNo, "http://assets.awn.site/voucher.png", null)))
                .isInstanceOf(IllegalArgumentException.class);

        // 拒绝后履约单仍待卖家交付
        assertThat(fulfillment.fulfillmentOf(lastOrderNo).status()).isEqualTo("PENDING_SELLER");
    }

    @Test
    void verificationRejectionEntersExceptionFlow() {
        paidOrder();
        drainOutbox();
        String fulfillmentNo = fulfillment.fulfillmentOf(lastOrderNo).fulfillmentNo();

        fulfillment.deliverSeller(new DeliverCommand(
                lastOrderNo, "https://img.awn.site/delivery.png", null));
        FulfillmentView rejected = fulfillment.verifyDelivery(new VerifyDeliveryCommand(
                lastOrderNo, false, "核验员-2", "凭证与订单不一致"));

        assertThat(rejected.status()).as("核验拒绝必须进入履约失败")
                .isEqualTo("FAILED");
        assertThat(rejected.failReason()).isEqualTo("凭证与订单不一致");
        // 失败必须可见（事件留痕），不是静默吞掉
        assertThat(outbox.countByEventTypeAndAggregateId("FULFILLMENT_FAILED", fulfillmentNo)).isEqualTo(1);

        // 事件闭环：FULFILLMENT_FAILED → 订单进入退款流程
        drainOutbox();
        assertThat(orderFacade.orderOf(lastOrderNo).status()).isEqualTo(OrderStatus.REFUND_PENDING);
    }

    @Test
    void sellerReportedFailureEntersExceptionFlow() {
        paidOrder();
        drainOutbox();
        String fulfillmentNo = fulfillment.fulfillmentOf(lastOrderNo).fulfillmentNo();

        FulfillmentView failed = fulfillment.reportFailure(lastOrderNo, "卖家取消：无法履约");
        assertThat(failed.status()).isEqualTo("FAILED");
        assertThat(outbox.countByEventTypeAndAggregateId("FULFILLMENT_FAILED", fulfillmentNo)).isEqualTo(1);

        drainOutbox();
        assertThat(orderFacade.orderOf(lastOrderNo).status()).isEqualTo(OrderStatus.REFUND_PENDING);

        // 事件重放幂等：不会重复推进订单状态机
        drainOutbox();
        assertThat(orderFacade.orderOf(lastOrderNo).status()).isEqualTo(OrderStatus.REFUND_PENDING);
    }

    @Test
    void oneFulfillmentPerOrderSurvivesEventRedelivery() {
        OrderView order = newPaidPendingOrder(1);
        PaymentView payment = payments.createPayment(
                new CreatePaymentCommand(order.orderNo(), "sandbox", "CNY", order.payAmount()));
        sandbox.succeedCharge(payment.channelTxnNo());

        // 渠道重复回调（Phase 04 幂等）+ 多轮分发：履约单仍只有一张
        payments.handleCallback(success(payment));
        payments.handleCallback(success(payment));
        drainOutbox();
        drainOutbox();

        FulfillmentView view = fulfillment.fulfillmentOf(order.orderNo());
        assertThat(view.status()).isEqualTo("PENDING_SELLER");
    }

    // ------------------------------------------------------------------

    private String lastOrderNo;

    /** 建单 → 支付成功 → 事件闭环（订单 PAID） */
    private void paidOrder() {
        OrderView order = newPaidPendingOrder(1);
        lastOrderNo = order.orderNo();
        PaymentView payment = payments.createPayment(
                new CreatePaymentCommand(order.orderNo(), "sandbox", "CNY", order.payAmount()));
        sandbox.succeedCharge(payment.channelTxnNo());
        payments.handleCallback(success(payment));
    }

    private PaymentCallbackCommand success(PaymentView payment) {
        return new PaymentCallbackCommand("sandbox", "{}",
                Map.of(SandboxPaymentGateway.SIGNATURE_HEADER, "awn-sandbox"),
                payment.channelTxnNo(), payment.currency(), payment.amount(), true);
    }

    /** 等价 Worker 连续分发直到本轮无到期消息 */
    private void drainOutbox() {
        for (int i = 0; i < 60 && dispatcher.dispatchDue(Instant.now()) > 0; i++) { }
    }

    /** 待支付订单：目录价 10000（核价契约后行单价必须等于目录价） */
    private OrderView newPaidPendingOrder(int quantity) {
        String suffix = UUID.randomUUID().toString().substring(0, 12);
        String poolCode = "pool-" + suffix;
        String productCode = products.productCode(suffix);
        inventory.createPool(new CreatePoolCommand(poolCode, productCode, "sess-" + suffix, quantity * 5));
        var line = new OrderLineCommand(poolCode, productCode, "履约测试演出",
                "sess-" + suffix, null, quantity, 10000, 0, 0);
        return orderFacade.createOrder(new CreateOrderCommand("ful-" + suffix, 42L, "CNY", List.of(line)));
    }
}
