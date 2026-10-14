package site.awn.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import site.awn.catalog.api.CatalogFacade;
import site.awn.catalog.api.CreateCategoryCommand;
import site.awn.catalog.api.CreateProductCommand;
import site.awn.catalog.domain.ProductStatus;
import site.awn.event.api.CreateEventCommand;
import site.awn.event.api.CreateSessionCommand;
import site.awn.event.api.CreateVenueCommand;
import site.awn.event.api.EventFacade;
import site.awn.event.api.EventView;
import site.awn.event.api.SessionView;
import site.awn.event.api.VenueView;
import site.awn.inventory.api.CreatePoolCommand;
import site.awn.inventory.api.InventoryFacade;
import site.awn.inventory.api.ReserveInventoryCommand;
import site.awn.support.AbstractPostgresIntegrationTest;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 公共商品接口集成测试（真实 PostgreSQL，todo Phase 02 验收口径）：
 * 详情、可售摘要与售罄态展示、未知商品 404 统一错误信封。
 * 可售数量为缓存口径；下单重校验由 OrderWriteService 的原子预留测试覆盖（OrderCoreTests）。
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProductApiTests extends AbstractPostgresIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired EventFacade events;
    @Autowired CatalogFacade catalog;
    @Autowired InventoryFacade inventory;

    private String uid() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private record Fixture(String productCode, String poolCode, SessionView session, long eventId) {
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
        return new Fixture(productCode, poolCode, session, event.id());
    }

    @Test
    void unknownProductReturnsNotFoundEnvelope() throws Exception {
        mvc.perform(get("/api/v1/products/missing-" + uid()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void productDetailMatchesCreatedFields() throws Exception {
        Fixture f = createProductWithPool(uid(), 10);

        mvc.perform(get("/api/v1/products/" + f.productCode()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(f.productCode()))
                .andExpect(jsonPath("$.name").value("VIP票·第一场"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.unitPrice").value(199900));
    }

    @Test
    void availabilityReflectsPoolAndSoldOutState() throws Exception {
        Fixture f = createProductWithPool(uid(), 2);

        mvc.perform(get("/api/v1/products/" + f.productCode() + "/availability"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(f.productCode()))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.available").value(2));

        // 售罄态：全部预留后 available 归 0（展示口径，与下单重校验解耦）
        String reservationNo = inventory.reserve(
                new ReserveInventoryCommand(f.poolCode(), "ORD-" + uid(), 2, null)).reservationNo();

        mvc.perform(get("/api/v1/products/" + f.productCode() + "/availability"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(0));

        // 释放后展示值回补
        assertThat(inventory.release(reservationNo)).isTrue();
        mvc.perform(get("/api/v1/products/" + f.productCode() + "/availability"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(2));
    }

    @Test
    void productWithoutPoolReportsNotOnSale() throws Exception {
        String suffix = uid();
        VenueView venue = events.createVenue(new CreateVenueCommand(
                "v-" + suffix, "场馆", "北京", null));
        Instant start = Instant.now().plusSeconds(3600);
        EventView event = events.createEvent(new CreateEventCommand(
                "e-" + suffix, "演唱会", null, start, start.plusSeconds(7200), venue.id()));
        SessionView session = events.createSession(new CreateSessionCommand(
                "s-" + suffix, event.id(), "第一场", start, start.plusSeconds(7200)));
        var category = catalog.createCategory(new CreateCategoryCommand(
                "c-" + suffix, "普通票", null));
        String productCode = "p-" + suffix;
        catalog.createProduct(new CreateProductCommand(
                productCode, event.id(), session.id(), category.id(), "普通票", 59900L));

        mvc.perform(get("/api/v1/products/" + productCode + "/availability"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(productCode))
                .andExpect(jsonPath("$.sellable").value(false))
                .andExpect(jsonPath("$.total").doesNotExist())
                .andExpect(jsonPath("$.available").doesNotExist());
    }

    @Test
    void sellableRequiresPublishedStatus() {
        // PUBLISHED + 有余量才可售；DRAFT 与售罄均为 false（发布流属 Phase 02 后续条目）
        assertThat(AvailabilityResponse.of(ProductStatus.PUBLISHED, "p", 10, 5).sellable()).isTrue();
        assertThat(AvailabilityResponse.of(ProductStatus.DRAFT, "p", 10, 5).sellable()).isFalse();
        assertThat(AvailabilityResponse.of(ProductStatus.PUBLISHED, "p", 10, 0).sellable()).isFalse();
    }

    @Test
    void sessionProductsListReturnsPublishedWithAvailability() throws Exception {
        String suffix = uid();
        VenueView venue = events.createVenue(new CreateVenueCommand(
                "v-" + suffix, "场馆", "北京", null));
        Instant start = Instant.now().plusSeconds(3600);
        EventView event = events.createEvent(new CreateEventCommand(
                "e-" + suffix, "演唱会", null, start, start.plusSeconds(7200), venue.id()));
        SessionView session = events.createSession(new CreateSessionCommand(
                "s-" + suffix, event.id(), "第一场", start, start.plusSeconds(7200)));
        var category = catalog.createCategory(new CreateCategoryCommand(
                "c-" + suffix, "VIP票", null));

        // 已发布 + 有库存池：可售数量进列表
        catalog.createProduct(new CreateProductCommand(
                "p-a-" + suffix, event.id(), session.id(), category.id(), "VIP票·第一场", 199900L));
        catalog.publishProduct("p-a-" + suffix);
        inventory.createPool(new CreatePoolCommand(
                "pool-a-" + suffix, "p-a-" + suffix, "s-" + suffix, 4));
        // 已发布但无池：available 缺省（未开售）
        catalog.createProduct(new CreateProductCommand(
                "p-c-" + suffix, event.id(), session.id(), category.id(), "普通票·第一场", 59900L));
        catalog.publishProduct("p-c-" + suffix);
        // 草稿商品不得出现在列表
        catalog.createProduct(new CreateProductCommand(
                "p-b-" + suffix, event.id(), session.id(), category.id(), "草稿票", 100L));

        mvc.perform(get("/api/v1/sessions/" + session.id() + "/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                // 按创建顺序（id 升序）返回
                .andExpect(jsonPath("$[0].productCode").value("p-a-" + suffix))
                .andExpect(jsonPath("$[0].status").value("PUBLISHED"))
                .andExpect(jsonPath("$[0].unitPrice").value(199900))
                .andExpect(jsonPath("$[0].categoryId").value(category.id()))
                .andExpect(jsonPath("$[0].sessionId").value(session.id()))
                .andExpect(jsonPath("$[0].available").value(4))
                .andExpect(jsonPath("$[1].productCode").value("p-c-" + suffix))
                .andExpect(jsonPath("$[1].available").doesNotExist());
    }

    @Test
    void sessionProductsListIsEmptyWhenNoPublishedProducts() throws Exception {
        Fixture f = createProductWithPool(uid(), 1);
        // 商品保持 DRAFT：列表为空而不是报错
        mvc.perform(get("/api/v1/sessions/" + f.session().id() + "/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void unknownSessionProductsReturnsNotFound() throws Exception {
        mvc.perform(get("/api/v1/sessions/999999999/products"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SESSION_NOT_FOUND"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }
}
