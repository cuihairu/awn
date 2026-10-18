package site.awn.payment;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import site.awn.inventory.api.InventoryFacade;
import site.awn.inventory.api.CreatePoolCommand;
import site.awn.order.api.CreateOrderCommand;
import site.awn.order.api.OrderFacade;
import site.awn.order.api.OrderLineCommand;
import site.awn.order.api.OrderView;
import site.awn.order.domain.OrderStatus;
import site.awn.payment.api.CreatePaymentCommand;
import site.awn.payment.api.GatewayChannelStatus;
import site.awn.payment.api.GatewayChargeCommand;
import site.awn.payment.api.GatewayChargeResult;
import site.awn.payment.api.GatewayQueryResult;
import site.awn.payment.api.GatewayRefundCommand;
import site.awn.payment.api.GatewayRefundResult;
import site.awn.payment.api.PaymentCallbackCommand;
import site.awn.payment.api.PaymentFacade;
import site.awn.payment.api.PaymentGateway;
import site.awn.payment.api.PaymentReconciliationReport;
import site.awn.payment.api.PaymentUnknownResolutionResult;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
    @Autowired JdbcTemplate jdbc;
    @Autowired site.awn.support.CatalogProductFactory products;

    /**
     * G5 验证用假渠道：down 受理即抛异常（模拟渠道故障），
     * flaky-refund 支付受理正常但退款受理抛异常。
     * 两者查询均返回 UNKNOWN（对账只报告不修复），验签均通过。
     */
    @TestConfiguration
    static class FailingGateways {

        @Bean
        PaymentGateway downGateway() {
            return new PaymentGateway() {
                @Override
                public String channelCode() {
                    return "down";
                }

                @Override
                public GatewayChargeResult createCharge(GatewayChargeCommand command) {
                    throw new IllegalStateException("channel down");
                }

                @Override
                public GatewayRefundResult createRefund(GatewayRefundCommand command) {
                    throw new IllegalStateException("channel down");
                }

                @Override
                public GatewayQueryResult queryCharge(String channelTxnNo) {
                    return new GatewayQueryResult(channelTxnNo, GatewayChannelStatus.UNKNOWN);
                }

                @Override
                public GatewayQueryResult queryRefund(String channelRefundNo) {
                    return new GatewayQueryResult(channelRefundNo, GatewayChannelStatus.UNKNOWN);
                }

                @Override
                public boolean verifyCallback(String rawBody, Map<String, String> headers) {
                    return true;
                }
            };
        }

        @Bean
        PaymentGateway flakyRefundGateway() {
            return new PaymentGateway() {
                @Override
                public String channelCode() {
                    return "flaky-refund";
                }

                @Override
                public GatewayChargeResult createCharge(GatewayChargeCommand command) {
                    return new GatewayChargeResult("FLK-" + UUID.randomUUID(), GatewayChannelStatus.PENDING);
                }

                @Override
                public GatewayRefundResult createRefund(GatewayRefundCommand command) {
                    throw new IllegalStateException("channel down");
                }

                @Override
                public GatewayQueryResult queryCharge(String channelTxnNo) {
                    return new GatewayQueryResult(channelTxnNo, GatewayChannelStatus.UNKNOWN);
                }

                @Override
                public GatewayQueryResult queryRefund(String channelRefundNo) {
                    return new GatewayQueryResult(channelRefundNo, GatewayChannelStatus.UNKNOWN);
                }

                @Override
                public boolean verifyCallback(String rawBody, Map<String, String> headers) {
                    return true;
                }
            };
        }
    }

    @Test
    void callbackSuccessClosesEventLoopToFulfillingOrder() {
        OrderView order = newPaidPendingOrder(2);
        PaymentView payment = payments.createPayment(
                new CreatePaymentCommand(order.orderNo(), "sandbox", "CNY", order.payAmount()));

        // 渠道侧事实：支付成功 → 回调
        sandbox.succeedCharge(payment.channelTxnNo());
        PaymentView after = payments.handleCallback(success(payment));

        assertThat(after.status()).isEqualTo(PaymentStatus.SUCCEEDED.name());
        assertThat(after.paidAt()).isNotNull();

        // 订单仍是待支付：确认由事件闭环完成
        assertThat(orderFacade.orderOf(order.orderNo()).status()).isEqualTo(OrderStatus.PENDING_PAYMENT);

        // Worker 分发（积压耗尽，等价 worker 连续多轮）→ 库存转已售，履约单创建 → 订单履约中
        drainOutbox();
        assertThat(orderFacade.orderOf(order.orderNo()).status()).isEqualTo(OrderStatus.FULFILLING);
        var pool = inventory.balanceOf(order.items().get(0).poolCode());
        assertThat(pool.soldQuantity()).isEqualTo(2);
        assertThat(pool.reservedQuantity()).isZero();

        // 重启 / 重复分发：Inbox 去重，事件不重复生效
        dispatcher.dispatchDue(Instant.now().plusSeconds(600));
        drainOutbox();
        var poolAgain = inventory.balanceOf(order.items().get(0).poolCode());
        assertThat(poolAgain.soldQuantity()).as("同一事件不得重复消费").isEqualTo(2);
        assertThat(orderFacade.orderOf(order.orderNo()).status()).isEqualTo(OrderStatus.FULFILLING);
    }

    @Test
    void duplicateCallbackTakesEffectOnce() {
        OrderView order = newPaidPendingOrder(1);
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
        OrderView order = newPaidPendingOrder(1);
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
        OrderView order = newPaidPendingOrder(1);
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
        OrderView order = newPaidPendingOrder(1);
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
        OrderView order = newPaidPendingOrder(1);
        PaymentView payment = payments.createPayment(
                new CreatePaymentCommand(order.orderNo(), "sandbox", "CNY", order.payAmount()));
        sandbox.succeedCharge(payment.channelTxnNo());
        payments.handleCallback(success(payment));
        drainOutbox();

        // 部分退款
        String key = "refund-" + UUID.randomUUID();
        RefundView partial = payments.requestRefund(
                new RequestRefundCommand(payment.paymentNo(), key, 4000, "partial"));
        assertThat(partial.status()).isEqualTo("PENDING");
        sandbox.succeedRefund(partial.channelRefundNo());
        RefundView done = payments.handleRefundCallback(refundCallback(partial));
        assertThat(done.status()).isEqualTo("SUCCEEDED");

        // 重复退款回调幂等
        RefundView replay = payments.handleRefundCallback(refundCallback(partial));
        assertThat(replay.status()).isEqualTo("SUCCEEDED");

        // 重复退款请求（同键同请求）→ 原退款单，不重复发起资金退款
        RefundView same = payments.requestRefund(
                new RequestRefundCommand(payment.paymentNo(), key, 4000, "partial"));
        assertThat(same.refundNo()).isEqualTo(partial.refundNo());

        // 支付单转部分退款
        assertThat(payments.payment(payment.paymentNo()).status())
                .isEqualTo(PaymentStatus.PARTIALLY_REFUNDED.name());

        // 超额退款被拒绝：剩余可退 6000，请求 6001
        assertThatThrownBy(() -> payments.requestRefund(
                new RequestRefundCommand(payment.paymentNo(), "refund-x-" + UUID.randomUUID(), 6001, "over")))
                .isInstanceOf(RefundConflictException.class)
                .hasMessageContaining("exceeds");

        // 全额退款补齐 → REFUNDED
        RefundView rest = payments.requestRefund(
                new RequestRefundCommand(payment.paymentNo(), "refund-rest-" + UUID.randomUUID(), 6000, "rest"));
        sandbox.succeedRefund(rest.channelRefundNo());
        payments.handleRefundCallback(refundCallback(rest));
        assertThat(payments.payment(payment.paymentNo()).status())
                .isEqualTo(PaymentStatus.REFUNDED.name());
    }

    @Test
    void refundNotAllowedBeforePaymentSucceeds() {
        OrderView order = newPaidPendingOrder(1);
        PaymentView payment = payments.createPayment(
                new CreatePaymentCommand(order.orderNo(), "sandbox", "CNY", order.payAmount()));

        assertThatThrownBy(() -> payments.requestRefund(
                new RequestRefundCommand(payment.paymentNo(), "refund-early-" + UUID.randomUUID(), 500, null)))
                .isInstanceOf(PaymentConflictException.class)
                .hasMessageContaining("not allowed");
    }

    @Test
    void concurrentRefundsCannotExceedThePaidAmount() throws Exception {
        OrderView order = newPaidPendingOrder(1);
        PaymentView payment = payments.createPayment(
                new CreatePaymentCommand(order.orderNo(), "sandbox", "CNY", order.payAmount()));
        sandbox.succeedCharge(payment.channelTxnNo());
        payments.handleCallback(success(payment));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Boolean> first = executor.submit(() -> requestPartialRefund(payment, start, "race-a-"));
            Future<Boolean> second = executor.submit(() -> requestPartialRefund(payment, start, "race-b-"));
            start.countDown();
            boolean firstAccepted = first.get(20, TimeUnit.SECONDS);
            boolean secondAccepted = second.get(20, TimeUnit.SECONDS);

            assertThat(List.of(firstAccepted, secondAccepted)).containsExactlyInAnyOrder(true, false);
            Long occupied = jdbc.queryForObject(
                    "SELECT COALESCE(SUM(amount),0) FROM refunds WHERE payment_no = ? "
                            + "AND status IN ('CREATED','PENDING','UNKNOWN','SUCCEEDED')",
                    Long.class, payment.paymentNo());
            assertThat(occupied).isLessThanOrEqualTo(payment.amount());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentPaymentCreationAllowsOnlyOneActivePaymentPerOrder() throws Exception {
        OrderView order = newPaidPendingOrder(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Boolean> first = executor.submit(() -> createPaymentIfAllowed(order, start));
            Future<Boolean> second = executor.submit(() -> createPaymentIfAllowed(order, start));
            start.countDown();
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
            Integer count = jdbc.queryForObject(
                    "SELECT count(*) FROM payments WHERE order_no = ? AND status <> 'FAILED'",
                    Integer.class, order.orderNo());
            assertThat(count).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private boolean createPaymentIfAllowed(OrderView order, CountDownLatch start) throws InterruptedException {
        start.await();
        try {
            payments.createPayment(new CreatePaymentCommand(order.orderNo(), "sandbox", "CNY", order.payAmount()));
            return true;
        } catch (PaymentConflictException expected) {
            return false;
        }
    }

    private boolean requestPartialRefund(PaymentView payment, CountDownLatch start, String keyPrefix)
            throws InterruptedException {
        start.await();
        try {
            payments.requestRefund(new RequestRefundCommand(payment.paymentNo(),
                    keyPrefix + UUID.randomUUID(), 7000, "concurrency test"));
            return true;
        } catch (RefundConflictException expected) {
            return false;
        }
    }

    @Test
    void chargeAcceptanceFailureKeepsCreatedPayment() {
        OrderView order = newPaidPendingOrder(1);

        // 渠道受理故障：不得回滚本地支付单，保留 CREATED 交对账闭环
        assertThatThrownBy(() -> payments.createPayment(
                new CreatePaymentCommand(order.orderNo(), "down", "CNY", order.payAmount())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("channel down");

        String status = jdbc.queryForObject(
                "SELECT status FROM payments WHERE order_no = ? AND channel = 'down'",
                String.class, order.orderNo());
        assertThat(status).as("渠道受理失败：支付单须保留 CREATED 待对账").isEqualTo("CREATED");
    }

    @Test
    void refundAcceptanceFailureKeepsCreatedRefund() {
        OrderView order = newPaidPendingOrder(1);
        PaymentView payment = payments.createPayment(
                new CreatePaymentCommand(order.orderNo(), "flaky-refund", "CNY", order.payAmount()));
        assertThat(payment.status()).isEqualTo("PENDING");

        // 渠道侧事实：支付成功 → 回调（flaky-refund 验签通过）
        PaymentView after = payments.handleCallback(new PaymentCallbackCommand(
                "flaky-refund", "{}", Map.of("X-Flaky-Signature", "ok"),
                payment.channelTxnNo(), "CNY", order.payAmount(), true));
        assertThat(after.status()).isEqualTo(PaymentStatus.SUCCEEDED.name());

        // 退款受理时渠道故障：退款单须保留 CREATED 交对账闭环
        assertThatThrownBy(() -> payments.requestRefund(
                new RequestRefundCommand(payment.paymentNo(),
                        "refund-flaky-" + UUID.randomUUID(), 400, "partial")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("channel down");

        String status = jdbc.queryForObject(
                "SELECT status FROM refunds WHERE payment_no = ?",
                String.class, payment.paymentNo());
        assertThat(status).as("渠道受理失败：退款单须保留 CREATED 待对账").isEqualTo("CREATED");
    }

    @Test
    void unknownPaymentResolvesToSucceededAndAutoRefundsAfterRelease() {
        OrderView order = newPaidPendingOrder(1);
        PaymentView payment = payments.createPayment(
                new CreatePaymentCommand(order.orderNo(), "sandbox", "CNY", order.payAmount()));
        sandbox.succeedCharge(payment.channelTxnNo());

        // 订单先到期释放，成功回调后到 → 支付单 UNKNOWN（不盲目确认）
        orderFacade.releaseExpiredOrders(Instant.now().plusSeconds(3600));
        assertThat(payments.handleCallback(success(payment)).status())
                .isEqualTo(PaymentStatus.UNKNOWN.name());

        // 消费者核实：渠道查实成功 → UNKNOWN → SUCCEEDED
        // 共享测试库可能含历史 UNKNOWN 支付单，计数只断言下限
        PaymentUnknownResolutionResult result = payments.resolveUnknownPayments();
        assertThat(result.resolvedSucceeded()).isGreaterThanOrEqualTo(1);
        assertThat(payments.payment(payment.paymentNo()).status())
                .isEqualTo(PaymentStatus.SUCCEEDED.name());

        // 异常单处理：已扣款但库存已释放 → 原路退款，不假装履约
        String channelRefundNo = jdbc.queryForObject(
                "SELECT channel_refund_no FROM refunds WHERE payment_no = ?",
                String.class, payment.paymentNo());
        assertThat(channelRefundNo).isNotNull();
        sandbox.succeedRefund(channelRefundNo);
        payments.handleRefundCallback(new RefundCallbackCommand(
                "sandbox", "{}", validSignature(), channelRefundNo, true));
        assertThat(payments.payment(payment.paymentNo()).status())
                .isEqualTo(PaymentStatus.REFUNDED.name());
    }

    @Test
    void unknownPaymentResolvesToFailedWhenChannelConfirmsNotPaid() {
        OrderView order = newPaidPendingOrder(1);
        PaymentView payment = payments.createPayment(
                new CreatePaymentCommand(order.orderNo(), "sandbox", "CNY", order.payAmount()));
        sandbox.failCharge(payment.channelTxnNo());

        orderFacade.releaseExpiredOrders(Instant.now().plusSeconds(3600));
        assertThat(payments.handleCallback(success(payment)).status())
                .isEqualTo(PaymentStatus.UNKNOWN.name());

        // 渠道查实未扣款 → FAILED；订单已释放，账实一致，无需退款
        payments.resolveUnknownPayments();
        assertThat(payments.payment(payment.paymentNo()).status())
                .isEqualTo(PaymentStatus.FAILED.name());
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM refunds WHERE payment_no = ?",
                Long.class, payment.paymentNo())).isZero();
    }

    @Test
    void unknownPaymentStaysWhenChannelStillPending() {
        OrderView order = newPaidPendingOrder(1);
        PaymentView payment = payments.createPayment(
                new CreatePaymentCommand(order.orderNo(), "sandbox", "CNY", order.payAmount()));
        // 渠道侧保持 PENDING：查实无结论

        orderFacade.releaseExpiredOrders(Instant.now().plusSeconds(3600));
        assertThat(payments.handleCallback(success(payment)).status())
                .isEqualTo(PaymentStatus.UNKNOWN.name());

        payments.resolveUnknownPayments();

        assertThat(payments.payment(payment.paymentNo()).status())
                .as("渠道未定：保持 UNKNOWN 待下轮核实").isEqualTo(PaymentStatus.UNKNOWN.name());
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM refunds WHERE payment_no = ?",
                Long.class, payment.paymentNo())).isZero();
    }

    @Test
    void duplicateRefundCallbackWritesEventOnce() {
        OrderView order = newPaidPendingOrder(1);
        PaymentView payment = payments.createPayment(
                new CreatePaymentCommand(order.orderNo(), "sandbox", "CNY", order.payAmount()));
        sandbox.succeedCharge(payment.channelTxnNo());
        payments.handleCallback(success(payment));

        RefundView refund = payments.requestRefund(new RequestRefundCommand(
                payment.paymentNo(), "refund-once-" + UUID.randomUUID(), order.payAmount(), null));
        sandbox.succeedRefund(refund.channelRefundNo());

        payments.handleRefundCallback(refundCallback(refund));
        payments.handleRefundCallback(refundCallback(refund));

        assertThat(outbox.countByEventTypeAndAggregateId("REFUND_SUCCEEDED", refund.refundNo()))
                .as("重复退款回调只产生一次有效退款事件").isEqualTo(1);
        assertThat(payments.payment(payment.paymentNo()).status())
                .isEqualTo(PaymentStatus.REFUNDED.name());
    }

    @Test
    void reconciliationDetectsChannelLocalDifferences() {
        OrderView order = newPaidPendingOrder(1);
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

    @Test
    void reconciliationReportsPaymentsOnUnregisteredChannel() {
        // 渠道下线 / 配置缺失后遗留的 PENDING 支付单：对账是只读报告，
        // 必须把「渠道无法核对」本身报告成差异，而不是让整个对账端点报错
        OrderView order = newPaidPendingOrder(1);
        String paymentNo = "pay-ghost-" + UUID.randomUUID();
        jdbc.update("INSERT INTO payments (payment_no, order_no, channel, status, currency, amount) "
                        + "VALUES (?, ?, 'ghost-channel', 'PENDING', 'CNY', 100)",
                paymentNo, order.orderNo());
        try {
            PaymentReconciliationReport report = payments.reconcile();
            assertThat(report.differences())
                    .as("未注册渠道的遗留支付单必须以差异形式呈现")
                    .anySatisfy(d -> {
                        assertThat(d.kind()).isEqualTo("PAYMENT");
                        assertThat(d.reference()).isEqualTo(paymentNo);
                        assertThat(d.localStatus()).isEqualTo("PENDING");
                        assertThat(d.channelStatus()).isEqualTo("UNSUPPORTED");
                    });
        } finally {
            jdbc.update("DELETE FROM payments WHERE payment_no = ?", paymentNo);
        }
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

    /** 待支付订单：目录价 10000（核价契约后行单价必须等于目录价） */
    private OrderView newPaidPendingOrder(int quantity) {
        String suffix = UUID.randomUUID().toString().substring(0, 12);
        String poolCode = "pool-" + suffix;
        String productCode = products.productCode(suffix);
        inventory.createPool(new CreatePoolCommand(poolCode, productCode,
                "sess-" + suffix, quantity * 5));
        var line = new OrderLineCommand(poolCode, productCode, "支付测试演出",
                "sess-" + suffix, null, quantity, 10000, 0, 0);
        return orderFacade.createOrder(new CreateOrderCommand("pay-" + suffix, 42L, "CNY", List.of(line)));
    }
}
