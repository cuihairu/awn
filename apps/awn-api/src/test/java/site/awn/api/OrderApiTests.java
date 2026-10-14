package site.awn.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import site.awn.catalog.api.CatalogFacade;
import site.awn.catalog.api.CreateCategoryCommand;
import site.awn.catalog.api.CreateProductCommand;
import site.awn.event.api.CreateEventCommand;
import site.awn.event.api.CreateSessionCommand;
import site.awn.event.api.CreateVenueCommand;
import site.awn.event.api.EventFacade;
import site.awn.event.api.EventView;
import site.awn.event.api.SessionView;
import site.awn.event.api.VenueView;
import site.awn.inventory.api.CreatePoolCommand;
import site.awn.inventory.api.InventoryFacade;
import site.awn.support.AbstractPostgresIntegrationTest;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 交易接口集成测试（真实 PostgreSQL）：订单创建/查询/取消三个 HTTP 端点。
 * 幂等键以 Idempotency-Key 请求头传入，与模块层同一语义；
 * 金额由服务端计算，客户端只传单价与优惠。
 */
@SpringBootTest
@AutoConfigureMockMvc
class OrderApiTests extends AbstractPostgresIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired EventFacade events;
    @Autowired CatalogFacade catalog;
    @Autowired InventoryFacade inventory;

    private String uid() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private record Fixture(String productCode, String poolCode, SessionView session) {
    }

    private Fixture createProductWithPool(String suffix, int total) {
        VenueView venue = events.createVenue(new CreateVenueCommand(
                "v-" + suffix, "场馆", "北京", null));
        Instant start = Instant.now().plusSeconds(3600);
        EventView event = events.createEvent(new CreateEventCommand(
                "e-" + suffix, "演唱会", null, start, start.plusSeconds(7200), venue.id()));
        SessionView session = events.createSession(new CreateSessionCommand(
                "s-" + suffix, event.id(), "第一场", start, start.plusSeconds(7200)));
        var category = catalog.createCategory(new CreateCategoryCommand(
                "c-" + suffix, "VIP票", null));
        String productCode = "p-" + suffix;
        catalog.createProduct(new CreateProductCommand(
                productCode, event.id(), session.id(), category.id(), "VIP票·第一场", 199900L));
        String poolCode = "pool-" + suffix;
        inventory.createPool(new CreatePoolCommand(poolCode, productCode,
                "s-" + suffix, total));
        return new Fixture(productCode, poolCode, session);
    }

    private String orderBody(Fixture f, int quantity, long unitPrice) {
        return """
                {"userId": 42, "currency": "CNY", "lines": [
                  {"poolCode": "%s", "productId": "%s", "productNameSnapshot": "VIP票·第一场",
                   "sessionId": "%s", "ticketCategorySnapshot": "VIP",
                   "quantity": %d, "unitPrice": %d, "discountAmount": 0, "feeAmount": 0}
                ]}
                """.formatted(f.poolCode(), f.productCode(), f.session().id(), quantity, unitPrice);
    }

    private String orderNoOf(String response) {
        return response.lines().filter(l -> l.contains("\"orderNo\"")).findFirst()
                .map(l -> l.split("\"")[3]).orElseThrow();
    }

    @Test
    void createOrderOverHttpReservesInventoryAndReturnsPendingPayment() throws Exception {
        Fixture f = createProductWithPool(uid(), 10);

        String response = mvc.perform(post("/api/v1/orders")
                        .header("Idempotency-Key", "k-" + uid())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(f, 2, 199900L)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/v1/orders/ORD-")))
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.currency").value("CNY"))
                .andExpect(jsonPath("$.totalAmount").value(399800))
                .andExpect(jsonPath("$.payAmount").value(399800))
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andReturn().getResponse().getContentAsString();

        // 库存已预留（模块层原子预留，同事务）
        assertThat(inventory.balanceOfProduct(f.productCode()))
                .isPresent()
                .get().extracting(b -> b.available()).isEqualTo(8);
    }

    @Test
    void replaySameIdempotencyKeyReturnsSameOrder() throws Exception {
        Fixture f = createProductWithPool(uid(), 10);
        String key = "k-" + uid();
        String body = orderBody(f, 2, 199900L);

        String first = mvc.perform(post("/api/v1/orders")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String second = mvc.perform(post("/api/v1/orders")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        // 同一幂等键 + 同一请求 → 同一订单（不产生第二单），库存只预留一次
        assertThat(orderNoOf(first)).isEqualTo(orderNoOf(second));
        assertThat(inventory.balanceOfProduct(f.productCode()))
                .isPresent()
                .get().extracting(b -> b.available()).isEqualTo(8);
    }

    @Test
    void sameKeyWithDifferentBodyReturnsIdempotencyConflict() throws Exception {
        Fixture f = createProductWithPool(uid(), 10);
        String key = "k-" + uid();

        mvc.perform(post("/api/v1/orders")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(f, 1, 199900L)))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/orders")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(f, 2, 199900L)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void missingIdempotencyKeyReturnsValidationFailed() throws Exception {
        Fixture f = createProductWithPool(uid(), 10);

        mvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(f, 1, 199900L)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("idempotencyKey is required"));
    }

    @Test
    void getOrderReturnsSnapshotAndUnknownReturnsNotFound() throws Exception {
        Fixture f = createProductWithPool(uid(), 10);
        String response = mvc.perform(post("/api/v1/orders")
                        .header("Idempotency-Key", "k-" + uid())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(f, 2, 199900L)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String orderNo = orderNoOf(response);

        mvc.perform(get("/api/v1/orders/" + orderNo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderNo").value(orderNo))
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].productNameSnapshot").value("VIP票·第一场"))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.items[0].unitPrice").value(199900))
                .andExpect(jsonPath("$.items[0].lineTotal").value(399800));

        mvc.perform(get("/api/v1/orders/ORD-missing-" + uid()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void cancelOrderReleasesInventoryAndIsIdempotent() throws Exception {
        Fixture f = createProductWithPool(uid(), 10);
        String response = mvc.perform(post("/api/v1/orders")
                        .header("Idempotency-Key", "k-" + uid())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(f, 2, 199900L)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String orderNo = orderNoOf(response);

        mvc.perform(post("/api/v1/orders/" + orderNo + "/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderNo").value(orderNo))
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        assertThat(inventory.balanceOfProduct(f.productCode()))
                .isPresent()
                .get().extracting(b -> b.available()).isEqualTo(10);

        // 幂等重放：重复取消返回同一订单的终态，不再释放
        mvc.perform(post("/api/v1/orders/" + orderNo + "/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        assertThat(inventory.balanceOfProduct(f.productCode()))
                .isPresent()
                .get().extracting(b -> b.available()).isEqualTo(10);
    }

    @Test
    void insufficientInventoryReturnsConflictAndReservesNothing() throws Exception {
        Fixture f = createProductWithPool(uid(), 2);

        mvc.perform(post("/api/v1/orders")
                        .header("Idempotency-Key", "k-" + uid())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(f, 5, 199900L)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_INVENTORY"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());

        // 事务回滚：库存未变，无残余预留
        assertThat(inventory.balanceOfProduct(f.productCode()))
                .isPresent()
                .get().extracting(b -> b.available()).isEqualTo(2);
    }
}
