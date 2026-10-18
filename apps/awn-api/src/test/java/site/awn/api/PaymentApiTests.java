package site.awn.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import site.awn.inventory.api.CreatePoolCommand;
import site.awn.inventory.api.InventoryFacade;
import site.awn.order.api.CreateOrderCommand;
import site.awn.order.api.OrderFacade;
import site.awn.order.api.OrderLineCommand;
import site.awn.order.api.OrderView;
import site.awn.payment.api.CreatePaymentCommand;
import site.awn.payment.api.PaymentCallbackCommand;
import site.awn.payment.api.PaymentFacade;
import site.awn.payment.api.PaymentView;
import site.awn.payment.sandbox.SandboxPaymentGateway;
import site.awn.support.AbstractPostgresIntegrationTest;
import site.awn.support.CatalogProductFactory;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 支付与退款 HTTP 接口集成测试（真实 PostgreSQL，todo 公开端点缺口验收口径）：
 * 建单支付（金额服务端定）、订单支付历史、退款受理（幂等键 + 订单归属校验）。
 * 回调验签、状态机与超退守卫的模块层语义由 PaymentModuleTests 覆盖；
 * 这里验证 HTTP 语义：状态码、错误信封、金额不收前端值。
 */
@SpringBootTest
@AutoConfigureMockMvc
class PaymentApiTests extends AbstractPostgresIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired OrderFacade orders;
    @Autowired PaymentFacade payments;
    @Autowired InventoryFacade inventory;
    @Autowired SandboxPaymentGateway sandbox;
    @Autowired CatalogProductFactory products;

    private String uid() {
        return UUID.randomUUID().toString().substring(0, 12);
    }

    /** 待支付订单：2 × 10000，payAmount = 20000（目录价权威，审核整改 P0） */
    private OrderView pendingOrder() {
        String suffix = uid();
        String poolCode = "pool-" + suffix;
        String productCode = products.productCode(suffix);
        inventory.createPool(new CreatePoolCommand(poolCode, productCode,
                "sess-" + suffix, 10));
        var line = new OrderLineCommand(poolCode, productCode, "支付接口测试演出",
                "sess-" + suffix, null, 2, 10000, 0, 0);
        return orders.createOrder(new CreateOrderCommand("pay-api-" + suffix, 42L, "CNY", List.of(line)));
    }

    private String paidOrderPayment() {
        OrderView order = pendingOrder();
        PaymentView payment = payments.createPayment(
                new CreatePaymentCommand(order.orderNo(), "sandbox", "CNY", order.payAmount()));
        sandbox.succeedCharge(payment.channelTxnNo());
        payments.handleCallback(new PaymentCallbackCommand("sandbox", "{}",
                Map.of(SandboxPaymentGateway.SIGNATURE_HEADER, "awn-sandbox"),
                payment.channelTxnNo(), "CNY", payment.amount(), true));
        return order.orderNo();
    }

    @Test
    void createPaymentUsesServerSideAmountAndReturnsCreated() throws Exception {
        OrderView order = pendingOrder();

        mvc.perform(post("/api/v1/orders/" + order.orderNo() + "/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"channel\":\"sandbox\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location",
                        "/api/v1/orders/" + order.orderNo() + "/payments"))
                .andExpect(jsonPath("$.orderNo").value(order.orderNo()))
                .andExpect(jsonPath("$.channel").value("sandbox"))
                // 金额 = 订单应付（服务端定），币种随订单
                .andExpect(jsonPath("$.amount").value((int) order.payAmount()))
                .andExpect(jsonPath("$.currency").value("CNY"))
                .andExpect(jsonPath("$.channelTxnNo").isNotEmpty())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void createPaymentForUnknownOrderReturnsOrderNotFound() throws Exception {
        mvc.perform(post("/api/v1/orders/ORD-missing-" + uid() + "/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"channel\":\"sandbox\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void createPaymentWithUnsupportedChannelConflicts() throws Exception {
        OrderView order = pendingOrder();

        mvc.perform(post("/api/v1/orders/" + order.orderNo() + "/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"channel\":\"no-such-channel\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_CONFLICT"));
    }

    @Test
    void paymentsListReturnsOnlyThatOrdersPayments() throws Exception {
        OrderView orderA = pendingOrder();
        OrderView orderB = pendingOrder();
        PaymentView first = payments.createPayment(
                new CreatePaymentCommand(orderA.orderNo(), "sandbox", "CNY", orderA.payAmount()));
        PaymentView second = payments.createPayment(
                new CreatePaymentCommand(orderB.orderNo(), "sandbox", "CNY", orderB.payAmount()));

        mvc.perform(get("/api/v1/orders/" + orderA.orderNo() + "/payments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].paymentNo").value(first.paymentNo()))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].orderNo").value(orderA.orderNo()));

        mvc.perform(get("/api/v1/orders/" + orderB.orderNo() + "/payments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].paymentNo").value(second.paymentNo()));
    }

    @Test
    void paymentsListIsEmptyBeforeAnyPaymentAndUnknownOrder404() throws Exception {
        OrderView order = pendingOrder();
        mvc.perform(get("/api/v1/orders/" + order.orderNo() + "/payments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mvc.perform(get("/api/v1/orders/ORD-missing-" + uid() + "/payments"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }

    @Test
    void refundAcceptanceReturnsPendingRefund() throws Exception {
        String orderNo = paidOrderPayment();
        String paymentNo = payments.paymentsOfOrder(orderNo).get(0).paymentNo();
        String key = "refund-api-" + uid();

        mvc.perform(post("/api/v1/orders/" + orderNo + "/refunds")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentNo\":\"" + paymentNo + "\",\"amount\":800,\"reason\":\"partial\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/orders/" + orderNo + "/refunds"))
                .andExpect(jsonPath("$.paymentNo").value(paymentNo))
                .andExpect(jsonPath("$.orderNo").value(orderNo))
                .andExpect(jsonPath("$.amount").value(800))
                .andExpect(jsonPath("$.reason").value("partial"))
                .andExpect(jsonPath("$.channelRefundNo").isNotEmpty())
                .andExpect(jsonPath("$.status").value("PENDING"));

        // 同键同请求重放：返回原退款单，不重复发起
        mvc.perform(post("/api/v1/orders/" + orderNo + "/refunds")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentNo\":\"" + paymentNo + "\",\"amount\":800,\"reason\":\"partial\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.channelRefundNo").isNotEmpty());
    }

    @Test
    void refundRequiresIdempotencyKey() throws Exception {
        String orderNo = paidOrderPayment();
        String paymentNo = payments.paymentsOfOrder(orderNo).get(0).paymentNo();

        mvc.perform(post("/api/v1/orders/" + orderNo + "/refunds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentNo\":\"" + paymentNo + "\",\"amount\":100}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void refundRejectsPaymentOfAnotherOrder() throws Exception {
        String orderA = paidOrderPayment();
        String orderB = paidOrderPayment();
        String paymentOfB = payments.paymentsOfOrder(orderB).get(0).paymentNo();

        mvc.perform(post("/api/v1/orders/" + orderA + "/refunds")
                        .header("Idempotency-Key", "cross-" + uid())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentNo\":\"" + paymentOfB + "\",\"amount\":100}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PAYMENT_NOT_FOUND"));
    }

    @Test
    void refundBeforePaymentSucceedsConflicts() throws Exception {
        OrderView order = pendingOrder();
        PaymentView payment = payments.createPayment(
                new CreatePaymentCommand(order.orderNo(), "sandbox", "CNY", order.payAmount()));

        mvc.perform(post("/api/v1/orders/" + order.orderNo() + "/refunds")
                        .header("Idempotency-Key", "early-" + uid())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentNo\":\"" + payment.paymentNo() + "\",\"amount\":100}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_CONFLICT"));
    }

    @Test
    void overRefundIsRejectedByQuotaGuard() throws Exception {
        String orderNo = paidOrderPayment();
        String paymentNo = payments.paymentsOfOrder(orderNo).get(0).paymentNo();

        mvc.perform(post("/api/v1/orders/" + orderNo + "/refunds")
                        .header("Idempotency-Key", "over-" + uid())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentNo\":\"" + paymentNo + "\",\"amount\":999999}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REFUND_CONFLICT"));
    }
}
