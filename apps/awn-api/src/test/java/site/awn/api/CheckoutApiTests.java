package site.awn.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import site.awn.inventory.api.CreatePoolCommand;
import site.awn.inventory.api.InventoryFacade;
import site.awn.order.api.CreateOrderCommand;
import site.awn.order.api.OrderFacade;
import site.awn.order.api.OrderLineCommand;
import site.awn.order.api.OrderView;
import site.awn.order.infrastructure.OrderRepository;
import site.awn.payment.api.CreatePaymentCommand;
import site.awn.payment.api.PaymentCallbackCommand;
import site.awn.payment.api.PaymentFacade;
import site.awn.payment.sandbox.SandboxPaymentGateway;
import site.awn.shared.outbox.OutboxDispatchService;
import site.awn.support.AbstractPostgresIntegrationTest;
import site.awn.support.CatalogProductFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 结算接口集成测试（真实 PostgreSQL，todo 公开端点缺口验收口径）：
 * 报价不含扣减（库存 / 挂牌 / 订单 / Outbox 全不动）、非法行 400；
 * 履约单查询：有履约 200，未知订单或未进入履约 404 统一错误信封。
 * 金额口径与下单一致由 OrderWriteService.computeAmounts 共用保证。
 */
@SpringBootTest
@AutoConfigureMockMvc
class CheckoutApiTests extends AbstractPostgresIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired OrderFacade orders;
    @Autowired PaymentFacade payments;
    @Autowired InventoryFacade inventory;
    @Autowired SandboxPaymentGateway sandbox;
    @Autowired OutboxDispatchService dispatcher;
    @Autowired CatalogProductFactory products;
    @Autowired OrderRepository orderRepository;

    private String uid() {
        return UUID.randomUUID().toString().substring(0, 12);
    }

    @Test
    void quoteComputesAmountsWithoutTouchingAnything() throws Exception {
        String suffix = uid();
        String poolCode = "pool-" + suffix;
        inventory.createPool(new CreatePoolCommand(poolCode, products.productCode(suffix),
                "sess-" + suffix, 10));

        // 测试库跨用例持久：用增量断言
        long ordersBefore = orderRepository.count();
        int availableBefore = inventory.balanceOf(poolCode).available();

        // 2 × 1000，行优惠 100，行费用 10 → total 2000 / discount 100 / fee 10 / pay 1910
        mvc.perform(post("/api/v1/checkout/quote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currency":"CNY","lines":[{"poolCode":"%s","productId":"p-%s",
                                "productNameSnapshot":"报价演出","quantity":2,"unitPrice":1000,
                                "discountAmount":100,"feeAmount":10}]}
                                """.formatted(poolCode, suffix)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("CNY"))
                .andExpect(jsonPath("$.totalAmount").value(2000))
                .andExpect(jsonPath("$.discountAmount").value(100))
                .andExpect(jsonPath("$.feeAmount").value(10))
                .andExpect(jsonPath("$.payAmount").value(1910));

        // 报价不含扣减：库存不动、订单不产生
        assertThat(inventory.balanceOf(poolCode).available()).isEqualTo(availableBefore);
        assertThat(inventory.balanceOf(poolCode).reservedQuantity()).isZero();
        assertThat(orderRepository.count()).isEqualTo(ordersBefore);
    }

    @Test
    void quoteMatchesOrderAmountsForSameLines() throws Exception {
        String suffix = uid();
        String poolCode = "pool-" + suffix;
        inventory.createPool(new CreatePoolCommand(poolCode, products.productCode(suffix),
                "sess-" + suffix, 10));

        String line = """
                {"poolCode":"%s","productId":"p-%s","productNameSnapshot":"口径演出",
                "quantity":3,"unitPrice":2500,"discountAmount":300,"feeAmount":0}
                """.formatted(poolCode, suffix);
        String quote = mvc.perform(post("/api/v1/checkout/quote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currency\":\"CNY\",\"lines\":[" + line + "]}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(quote).contains("\"payAmount\":7200");

        // 同一行下单：金额与报价同口径（服务端同一计算路径）
        OrderView order = orders.createOrder(new CreateOrderCommand("quote-ord-" + suffix, 42L,
                "CNY", List.of(new OrderLineCommand(poolCode, "p-" + suffix, "口径演出",
                null, null, 3, 2500, 300, 0))));
        assertThat(order.totalAmount()).isEqualTo(7500);
        assertThat(order.discountAmount()).isEqualTo(300);
        assertThat(order.payAmount()).isEqualTo(7200);
    }

    @Test
    void quoteRejectsInvalidLines() throws Exception {
        // 负行合计：优惠大于行金额
        mvc.perform(post("/api/v1/checkout/quote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currency":"CNY","lines":[{"poolCode":"pool-x","productId":"p-x",
                                "quantity":1,"unitPrice":100,"discountAmount":200}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        // 双来源行
        mvc.perform(post("/api/v1/checkout/quote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currency":"CNY","lines":[{"poolCode":"pool-x","listingNo":"LST-x",
                                "productId":"p-x","quantity":1,"unitPrice":100}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        // 非正数量
        mvc.perform(post("/api/v1/checkout/quote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currency":"CNY","lines":[{"poolCode":"pool-x","productId":"p-x",
                                "quantity":0,"unitPrice":100}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void unknownOrderFulfillmentReturnsNotFoundEnvelope() throws Exception {
        mvc.perform(get("/api/v1/orders/ORD-missing-" + uid() + "/fulfillment"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void unpaidOrderHasNoFulfillment() throws Exception {
        String suffix = uid();
        String poolCode = "pool-" + suffix;
        inventory.createPool(new CreatePoolCommand(poolCode, products.productCode(suffix),
                "sess-" + suffix, 10));
        OrderView order = orders.createOrder(new CreateOrderCommand("chk-" + suffix, 42L,
                "CNY", List.of(new OrderLineCommand(poolCode, "p-" + suffix, "履约查询演出",
                null, null, 1, 500, 0, 0))));

        // 待支付订单没有履约单：404 而不是空视图
        mvc.perform(get("/api/v1/orders/" + order.orderNo() + "/fulfillment"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void fulfillmentReturnsViewAfterPaymentEventLoop() throws Exception {
        String suffix = uid();
        String poolCode = "pool-" + suffix;
        inventory.createPool(new CreatePoolCommand(poolCode, products.productCode(suffix),
                "sess-" + suffix, 10));
        OrderView order = orders.createOrder(new CreateOrderCommand("chk-paid-" + suffix, 42L,
                "CNY", List.of(new OrderLineCommand(poolCode, "p-" + suffix, "履约查询演出",
                null, null, 1, 500, 0, 0))));

        var payment = payments.createPayment(new CreatePaymentCommand(
                order.orderNo(), "sandbox", "CNY", order.payAmount()));
        sandbox.succeedCharge(payment.channelTxnNo());
        payments.handleCallback(new PaymentCallbackCommand("sandbox", "{}",
                Map.of(SandboxPaymentGateway.SIGNATURE_HEADER, "awn-sandbox"),
                payment.channelTxnNo(), "CNY", payment.amount(), true));
        for (int i = 0; i < 50 && dispatcher.dispatchDue(Instant.now()) > 0; i++) { }

        mvc.perform(get("/api/v1/orders/" + order.orderNo() + "/fulfillment"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderNo").value(order.orderNo()))
                .andExpect(jsonPath("$.fulfillmentNo").isNotEmpty())
                // 支付成功不自动等于交付成功：停在待卖家交付
                .andExpect(jsonPath("$.status").value("PENDING_SELLER"));
    }
}
