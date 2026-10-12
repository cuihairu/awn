package site.awn.payment;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import site.awn.inventory.api.InventoryFacade;
import site.awn.inventory.api.CreatePoolCommand;
import site.awn.order.api.CreateOrderCommand;
import site.awn.order.api.OrderFacade;
import site.awn.order.api.OrderLineCommand;
import site.awn.order.api.OrderView;
import site.awn.order.domain.OrderStatus;
import site.awn.payment.api.CreatePaymentCommand;
import site.awn.payment.api.PaymentCallbackCommand;
import site.awn.payment.api.PaymentFacade;
import site.awn.payment.api.PaymentReconciliationReport;
import site.awn.payment.api.PaymentView;
import site.awn.payment.api.RefundCallbackCommand;
import site.awn.payment.api.RefundView;
import site.awn.payment.api.RequestRefundCommand;
import site.awn.payment.domain.PaymentCallbackRejectedException;
import site.awn.payment.domain.PaymentConflictException;
import site.awn.payment.domain.PaymentStatus;
import site.awn.payment.domain.RefundConflictException;
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
 * Phase 04：支付回调（验签/去重/幂等）、退款状态机、Outbox 事件闭环、渠道对账。
 * 验收口径：
 * - 重复回调只生效一次；验签失败拒绝；回调与超时释放竞态进入「待核实」而非盲目确认；
 * - 重复退款请求幂等；累计退款不超支付金额；
 * - 事件闭环：PAYMENT_SUCCEEDED → Worker 分发 → 订单 PAID；进程重启不丢、Inbox 去重；
 * - 模拟渠道与本地状态差异可被发现。
 */
@SpringBootTest
class PaymentModuleTests extends AbstractPostgresIntegrationTest {

    @Autowired PaymentFacade payments;
    @Autowired OrderFacade orderFacade;
    @Autowired InventoryFacade inventory;
    @Autowired OutboxDispatchService dispatcher;
    @Autowired OutboxMessageRepository outbox;
    @Autowired SandboxPaymentGateway sandbox;

    @Test
    void callbackSuccessClosesEventLoopToPaidOrder() {
        OrderView order = newPaidPendingOrder(2, 1000);
        PaymentView payment = payments.createPayment(
                new CreatePaymentCommand(order.orderNo(), "sandbox", "CNY", order.payAmount()));

        // 渠道侧事实：支付成功 → 回调
        sandbox.succeedCharge(payment.channelTxnNo());
        PaymentView after = payments.handleCallback(success(payment));

        assertThat(after.status()).isEqualTo(PaymentStatus.SUCCEEDED.name());
        assertThat(after.paidAt()).isNotNull();

        // 订单仍是待支付：确认由事件闭环完成
        assertThat(orderFacade.orderOf(order.orderNo()).status()).isEqualTo(OrderStatus.PENDING_PAYMENT);

        // Worker 分发（积压耗尽，等价 worker 连续多轮）→ 订单 PAID，库存转已售
        drainOutbox();
        assertThat(orderFacade.orderOf(order.orderNo()).status()).isEqualTo(OrderStatus.PAID);
        var pool = inventory.balanceOf(order.items().get(0).poolCode());
        assertThat(pool.soldQuantity()).isEqualTo(2);
        assertThat(pool.reservedQuantity()).isZero();

        // 重启 / 重复分发：Inbox 去重，事件不重复生效
        dispatcher.dispatchDue(Instant.now().plusSeconds(600));
        drainOutbox();
        var poolAgain = inventory.balanceOf(order.items().get(0).poolCode());
        assertThat(poolAgain.soldQuantity()).as("同一事件不得重复消费").isEqualTo(2);
        assertThat(orderFacade.orderOf(order.orderNo()).status()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void duplicateCallbackTakesEffectOnce() {
        OrderView order = newPaidPendingOrder(1, 500);
        PaymentView payment = payments.createPayment(
                new CreatePaymentCommand(order.orderNo(), "sandbox", "CNY", order.payAmount()));
        sandbox.succeedCharge(payment.channelTxnNo());

        payments.handleCallback(success(payment));
        PaymentView replay = payments.handleCallback(success(payment));

        assertThat(replay.status()).isEqualTo(PaymentStatus.SUCCEEDED.name());
        // 成功事件只写一次
        assertThat(outbox.countByEventTypeAndAggregateId("PAYMENT_SUCCEEDED", payment.paymentNo()))
                .as("重复回调只产生一次有效业务确认").isEqualTo(1);
    }

    @Test
    void invalidSignatureIsRejected() {
        OrderView order = newPaidPendingOrder(1, 500);
        PaymentView payment = payments.createPayment(
                new CreatePaymentCommand(order.orderNo(), "sandbox", "CNY", order.payAmount()));
        sandbox.succeedCharge(payment.channelTxnNo());

        assertThatThrownBy(() -> payments.handleCallback(new PaymentCallbackCommand(
                "sandbox", "{}", Map.of("X-Sandbox-Signature", "forged"),
                payment.channelTxnNo(), "CNY", order.payAmount(), true)))
                .isInstanceOf(PaymentCallbackRejectedException.class);

        assertThat(payments.payment(payment.paymentNo()).status())
                .as("验签失败不得变更状态").isEqualTo(PaymentStatus.PENDING.name());
    }

    @Test
    void amountMismatchIsRejected() {
        OrderView order = newPaidPendingOrder(1, 500);
        PaymentView payment = payments.createPayment(
                new CreatePaymentCommand(order.orderNo(), "sandbox", "CNY", order.payAmount()));
        sandbox.succeedCharge(payment.channelTxnNo());

        assertThatThrownBy(() -> payments.handleCallback(new PaymentCallbackCommand(
                "sandbox", "{}", validSignature(), payment.channelTxnNo(),
                "CNY", order.payAmount() + 1, true)))
                .isInstanceOf(PaymentConflictException.class)
                .hasMessageContaining("amount mismatch");
    }

    @Test
    void callbackRacingTimeoutReleaseGoesUnknownNotPaid() {
        OrderView order = newPaidPendingOrder(1, 500);
        PaymentView payment = payments.createPayment(
                new CreatePaymentCommand(order.orderNo(), "sandbox", "CNY", order.payAmount()));
        sandbox.succeedCharge(payment.channelTxnNo());

        // 库存已合法释放（订单超时）
        orderFacade.releaseExpiredOrders(Instant.now().plusSeconds(3600));
        assertThat(orderFacade.orderOf(order.orderNo()).status()).isEqualTo(OrderStatus.EXPIRED);

        // 支付成功回调此时到达：进待核实，不盲目确认订单
        PaymentView after = payments.handleCallback(success(payment));
        assertThat(after.status()).as("竞态必须进入待核实").isEqualTo(PaymentStatus.UNKNOWN.name());
        assertThat(orderFacade.orderOf(order.orderNo()).status()).isEqualTo(OrderStatus.EXPIRED);
        assertThat(outbox.countByEventTypeAndAggregateId("PAYMENT_UNKNOWN", payment.paymentNo())).isEqualTo(1);

        // 分发不产生 PAYMENT_SUCCEEDED 消费（无该事件）
        drainOutbox();
        assertThat(orderFacade.orderOf(order.orderNo()).status()).isEqualTo(OrderStatus.EXPIRED);
    }

    @Test
    void refundLifecycleIsGuardedAndIdempotent() {
        OrderView order = newPaidPendingOrder(1, 1000);
        PaymentView payment = payments.createPayment(
                new CreatePaymentCommand(order.orderNo(), "sandbox", "CNY", order.payAmount()));
        sandbox.succeedCharge(payment.channelTxnNo());
        payments.handleCallback(success(payment));
        drainOutbox();

        // 部分退款
        String key = "refund-" + UUID.randomUUID();
        RefundView partial = payments.requestRefund(
                new RequestRefundCommand(payment.paymentNo(), key, 400, "partial"));
        assertThat(partial.status()).isEqualTo("PENDING");
        sandbox.succeedRefund(partial.channelRefundNo());
        RefundView done = payments.handleRefundCallback(refundCallback(partial));
        assertThat(done.status()).isEqualTo("SUCCEEDED");

        // 重复退款回调幂等
        RefundView replay = payments.handleRefundCallback(refundCallback(partial));
        assertThat(replay.status()).isEqualTo("SUCCEEDED");

        // 重复退款请求（同键同请求）→ 原退款单，不重复发起资金退款
        RefundView same = payments.requestRefund(
                new RequestRefundCommand(payment.paymentNo(), key, 400, "partial"));
        assertThat(same.refundNo()).isEqualTo(partial.refundNo());

        // 支付单转部分退款
        assertThat(payments.payment(payment.paymentNo()).status())
                .isEqualTo(PaymentStatus.PARTIALLY_REFUNDED.name());

        // 超额退款被拒绝：剩余可退 600，请求 601
        assertThatThrownBy(() -> payments.requestRefund(
                new RequestRefundCommand(payment.paymentNo(), "refund-x-" + UUID.randomUUID(), 601, "over")))
                .isInstanceOf(RefundConflictException.class)
                .hasMessageContaining("exceeds");

        // 全额退款补齐 → REFUNDED
        RefundView rest = payments.requestRefund(
                new RequestRefundCommand(payment.paymentNo(), "refund-rest-" + UUID.randomUUID(), 600, "rest"));
        sandbox.succeedRefund(rest.channelRefundNo());
        payments.handleRefundCallback(refundCallback(rest));
        assertThat(payments.payment(payment.paymentNo()).status())
                .isEqualTo(PaymentStatus.REFUNDED.name());
    }

    @Test
    void refundNotAllowedBeforePaymentSucceeds() {
        OrderView order = newPaidPendingOrder(1, 500);
        PaymentView payment = payments.createPayment(
                new CreatePaymentCommand(order.orderNo(), "sandbox", "CNY", order.payAmount()));

        assertThatThrownBy(() -> payments.requestRefund(
                new RequestRefundCommand(payment.paymentNo(), "refund-early-" + UUID.randomUUID(), 500, null)))
                .isInstanceOf(PaymentConflictException.class)
                .hasMessageContaining("not allowed");
    }

    @Test
    void reconciliationDetectsChannelLocalDifferences() {
        OrderView order = newPaidPendingOrder(1, 500);
        PaymentView payment = payments.createPayment(
                new CreatePaymentCommand(order.orderNo(), "sandbox", "CNY", order.payAmount()));

        // 本地 PENDING + 渠道 PENDING → 账实相符
        PaymentReconciliationReport before = payments.reconcile();
        assertThat(before.differences()).isEmpty();

        // 渠道已成功但本地未跟上（漏回调）→ 差异被发现
        sandbox.succeedCharge(payment.channelTxnNo());
        PaymentReconciliationReport after = payments.reconcile();
        assertThat(after.consistent()).as("漏回调差异必须被发现").isFalse();
        assertThat(after.differences()).hasSize(1);
        var d = after.differences().get(0);
        assertThat(d.kind()).isEqualTo("PAYMENT");
        assertThat(d.reference()).isEqualTo(payment.paymentNo());
        assertThat(d.localStatus()).isEqualTo("PENDING");
        assertThat(d.channelStatus()).isEqualTo("SUCCEEDED");

        // 退款差异：渠道成功但退款回调未到
        payments.handleCallback(success(payment));
        String key = "refund-recon-" + UUID.randomUUID();
        RefundView refund = payments.requestRefund(
                new RequestRefundCommand(payment.paymentNo(), key, 500, null));
        PaymentReconciliationReport cleanRefund = payments.reconcile();
        assertThat(cleanRefund.differences()).isEmpty();

        sandbox.succeedRefund(refund.channelRefundNo());
        PaymentReconciliationReport refundDiff = payments.reconcile();
        assertThat(refundDiff.differences()).hasSize(1);
        assertThat(refundDiff.differences().get(0).kind()).isEqualTo("REFUND");
    }

    // ------------------------------------------------------------------

    private PaymentCallbackCommand success(PaymentView payment) {
        return new PaymentCallbackCommand("sandbox", "{}", validSignature(),
                payment.channelTxnNo(), payment.currency(), payment.amount(), true);
    }

    private RefundCallbackCommand refundCallback(RefundView refund) {
        return new RefundCallbackCommand("sandbox", "{}", validSignature(),
                refund.channelRefundNo(), true);
    }

    private Map<String, String> validSignature() {
        return Map.of(SandboxPaymentGateway.SIGNATURE_HEADER, "awn-sandbox");
    }

    /**
     * 等价 Worker 连续分发直到本轮无到期消息：测试库常积压历史 PENDING，
     * 单轮 50 条认领可能覆盖不到本用例事件。
     */
    private void drainOutbox() {
        for (int i = 0; i < 50 && dispatcher.dispatchDue(Instant.now()) > 0; i++) { }
    }

    private OrderView newPaidPendingOrder(int quantity, long unitPrice) {
        String suffix = UUID.randomUUID().toString().substring(0, 12);
        String poolCode = "pool-" + suffix;
        inventory.createPool(new CreatePoolCommand(poolCode, "prod-" + suffix,
                "sess-" + suffix, quantity * 5));
        var line = new OrderLineCommand(poolCode, "prod-" + suffix, "支付测试演出",
                "sess-" + suffix, null, quantity, unitPrice, 0, 0);
        return orderFacade.createOrder(new CreateOrderCommand("pay-" + suffix, 42L, "CNY", List.of(line)));
    }
}
