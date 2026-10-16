package site.awn.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import site.awn.inventory.api.CreatePoolCommand;
import site.awn.inventory.api.InventoryFacade;
import site.awn.order.api.CreateOrderCommand;
import site.awn.order.api.JoinWaitlistCommand;
import site.awn.order.api.OrderFacade;
import site.awn.order.api.OrderLineCommand;
import site.awn.order.api.OrderView;
import site.awn.order.api.WaitlistFacade;
import site.awn.order.api.WaitlistView;
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
import site.awn.payment.api.PaymentView;
import site.awn.payment.sandbox.SandboxPaymentGateway;
import site.awn.support.AbstractPostgresIntegrationTest;
import site.awn.support.CatalogProductFactory;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 购买流余留的 HTTP 契约（todo 146 余项）：
 *
 * <ul>
 *   <li>订单支付倒计时（服务端时间锚点，不收客户端时钟）；</li>
 *   <li>支付失败重试与换渠道（渠道清单由后端下发，不在前端硬编码）；</li>
 *   <li>排队 / 候补端点（排队中、退出、我的候补）。</li>
 * </ul>
 *
 * 模块层语义（递补、席位状态机、并发守卫）由 WaitlistModuleTests 覆盖。
 */
@SpringBootTest
@AutoConfigureMockMvc
class PurchaseFlowApiTests extends AbstractPostgresIntegrationTest {

    /** 第二个渠道：验证换渠道不依赖前端硬编码渠道名 */
    @TestConfiguration
    static class AltChannel {

        @Bean
        PaymentGateway altGateway() {
            return new PaymentGateway() {
                @Override
                public String channelCode() {
                    return "alt-sandbox";
                }

                @Override
                public GatewayChargeResult createCharge(GatewayChargeCommand command) {
                    return new GatewayChargeResult("ALT-" + UUID.randomUUID(), GatewayChannelStatus.PENDING);
                }

                @Override
                public GatewayRefundResult createRefund(GatewayRefundCommand command) {
                    throw new IllegalStateException("refund not supported");
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

    @Autowired MockMvc mvc;
    @Autowired OrderFacade orders;
    @Autowired PaymentFacade payments;
    @Autowired WaitlistFacade waitlist;
    @Autowired InventoryFacade inventory;
    @Autowired SandboxPaymentGateway sandbox;
    @Autowired CatalogProductFactory products;
    @Autowired JdbcTemplate jdbc;

    private static final AtomicLong USER_SEQ = new AtomicLong(500_000L);

    @BeforeEach
    void clearWaitlistEntries() {
        jdbc.update("DELETE FROM waitlist_entries");
    }

    /** 换渠道重试遗留的 alt-sandbox PENDING 支付单只在本类上下文可查，
     *  留在库里会让其他类的全库对账扫描（payments.reconcile）扫到不可核对渠道。 */
    @AfterEach
    void clearAltChannelPayments() {
        jdbc.update("DELETE FROM payments WHERE channel = 'alt-sandbox'");
    }

    private String uid() {
        return UUID.randomUUID().toString().substring(0, 12);
    }

    /** 候补列表按用户维度聚合，用户编号在全量用例间取唯一值避免相互污染 */
    private long userId() {
        return USER_SEQ.getAndIncrement();
    }

    private record Fixture(String poolCode, String productCode, String sessionId) {
    }

    private Fixture fixture(int total) {
        String suffix = uid();
        String productCode = products.productCode(suffix);
        String poolCode = "pool-" + suffix;
        inventory.createPool(new CreatePoolCommand(poolCode, productCode, "sess-" + suffix, total));
        return new Fixture(poolCode, productCode, "sess-" + suffix);
    }

    private OrderView pendingOrder(Fixture f) {
        return orders.createOrder(new CreateOrderCommand("pf-" + uid(), 42L, "CNY", List.of(
                new OrderLineCommand(f.poolCode(), f.productCode(), "购买流测试票",
                        f.sessionId(), null, 2, 1000, 0, 0))));
    }

    private PaymentView failedPayment(OrderView order) {
        PaymentView payment = payments.createPayment(
                new CreatePaymentCommand(order.orderNo(), "sandbox", "CNY", order.payAmount()));
        sandbox.failCharge(payment.channelTxnNo());
        payments.handleCallback(new PaymentCallbackCommand("sandbox", "{}",
                Map.of(SandboxPaymentGateway.SIGNATURE_HEADER, "awn-sandbox"),
                payment.channelTxnNo(), "CNY", payment.amount(), false));
        return payments.payment(payment.paymentNo());
    }

    @Test
    void orderViewCarriesServerAnchoredCountdown() throws Exception {
        OrderView order = pendingOrder(fixture(10));

        mvc.perform(get("/api/v1/orders/" + order.orderNo()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andExpect(jsonPath("$.serverTime").isNotEmpty())
                .andExpect(jsonPath("$.payExpiresInSeconds").isNumber());
    }

    @Test
    void countdownIsZeroOnceTheOrderIsNoLongerPayable() throws Exception {
        OrderView order = pendingOrder(fixture(10));

        mvc.perform(post("/api/v1/orders/" + order.orderNo() + "/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.payExpiresInSeconds").value(0));
    }

    @Test
    void channelsEndpointListsRegisteredGateways() throws Exception {
        mvc.perform(get("/api/v1/payments/channels"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0]").value("alt-sandbox"))
                .andExpect(jsonPath("$[1]").value("sandbox"));
    }

    @Test
    void failedPaymentIsRetryableAndRetryKeepsOrderSnapshotAmount() throws Exception {
        OrderView order = pendingOrder(fixture(10));
        PaymentView failed = failedPayment(order);
        assertThat(failed.retryable()).isTrue();

        mvc.perform(get("/api/v1/orders/" + order.orderNo() + "/payments"))
                .andExpect(jsonPath("$[0].retryable").value(true));

        mvc.perform(post("/api/v1/payments/" + failed.paymentNo() + "/retry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderNo").value(order.orderNo()))
                .andExpect(jsonPath("$.channel").value("sandbox"))
                .andExpect(jsonPath("$.amount").value((int) order.payAmount()))
                .andExpect(jsonPath("$.paymentNo").value(
                        org.hamcrest.Matchers.not(failed.paymentNo())));

        // 原支付单保持失败态，历史不改写
        assertThat(payments.payment(failed.paymentNo()).status()).isEqualTo("FAILED");
        assertThat(payments.payment(failed.paymentNo()).retryable()).isFalse();
    }

    @Test
    void retryCanSwitchToAnotherChannel() throws Exception {
        OrderView order = pendingOrder(fixture(10));
        PaymentView failed = failedPayment(order);

        mvc.perform(post("/api/v1/payments/" + failed.paymentNo() + "/retry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"channel\":\"alt-sandbox\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.channel").value("alt-sandbox"));
    }

    @Test
    void retryRejectsPaymentsThatHaveNotFailed() throws Exception {
        OrderView order = pendingOrder(fixture(10));
        PaymentView pending = payments.createPayment(
                new CreatePaymentCommand(order.orderNo(), "sandbox", "CNY", order.payAmount()));

        mvc.perform(post("/api/v1/payments/" + pending.paymentNo() + "/retry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_CONFLICT"));
    }

    @Test
    void retryIsRejectedWhenTheOrderDeadlinePassed() throws Exception {
        OrderView order = pendingOrder(fixture(10));
        PaymentView failed = failedPayment(order);
        orders.releaseExpiredOrders(java.time.Instant.now().plusSeconds(3600));

        mvc.perform(post("/api/v1/payments/" + failed.paymentNo() + "/retry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_CONFLICT"));
    }

    @Test
    void retryRejectsUnknownPayment() throws Exception {
        mvc.perform(post("/api/v1/payments/PAY-missing/retry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PAYMENT_NOT_FOUND"));
    }

    @Test
    void waitlistEndpointsExposeQueueState() throws Exception {
        Fixture f = fixture(2);
        long user = userId();
        String body = """
                {"sessionId":"%s","poolCode":"%s","productId":"%s","productName":"候补测试票",
                 "quantity":2,"unitPrice":1000,"currency":"CNY","userId":%d}
                """.formatted(f.sessionId(), f.poolCode(), f.productCode(), user);

        String waitlistNo = mvc.perform(post("/api/v1/waitlist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.waitlistNo").isNotEmpty())
                .andExpect(jsonPath("$.status").value("WAITING"))
                .andExpect(jsonPath("$.position").value(1))
                .andReturn().getResponse().getContentAsString()
                .lines().filter(l -> l.contains("\"waitlistNo\"")).findFirst()
                .map(l -> l.split("\"")[3]).orElseThrow();

        mvc.perform(get("/api/v1/waitlist/" + waitlistNo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WAITING"))
                .andExpect(jsonPath("$.position").value(1))
                .andExpect(jsonPath("$.orderNo").isEmpty());

        mvc.perform(get("/api/v1/waitlist").param("userId", String.valueOf(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].waitlistNo").value(waitlistNo));

        mvc.perform(post("/api/v1/waitlist/" + waitlistNo + "/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canceled").value(true));
        // 重复取消幂等
        mvc.perform(post("/api/v1/waitlist/" + waitlistNo + "/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canceled").value(false));
    }

    @Test
    void waitlistListRequiresUserId() throws Exception {
        mvc.perform(get("/api/v1/waitlist"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void waitlistRejectsDuplicateQueueingAndUnknownEntries() throws Exception {
        Fixture f = fixture(2);
        long user = userId();
        WaitlistView first = waitlist.join(new JoinWaitlistCommand(f.sessionId(), f.poolCode(),
                f.productCode(), "候补测试票", 1, 1000, "CNY", user));

        mvc.perform(post("/api/v1/waitlist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sessionId":"%s","poolCode":"%s","productId":"%s","productName":"候补测试票",
                                 "quantity":1,"unitPrice":1000,"currency":"CNY","userId":%d}
                                """.formatted(f.sessionId(), f.poolCode(), f.productCode(), user)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WAITLIST_CONFLICT"));

        mvc.perform(get("/api/v1/waitlist/WL-missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WAITLIST_NOT_FOUND"));
        assertThat(first.status()).isEqualTo("WAITING");
    }
}