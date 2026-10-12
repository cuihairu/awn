package site.awn.catalog;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import site.awn.catalog.api.CatalogFacade;
import site.awn.catalog.api.CreateCategoryCommand;
import site.awn.catalog.api.CreateProductCommand;
import site.awn.catalog.api.ProductView;
import site.awn.catalog.api.TicketCategoryView;
import site.awn.catalog.domain.ProductStatus;
import site.awn.event.api.CreateEventCommand;
import site.awn.event.api.CreateSessionCommand;
import site.awn.event.api.CreateVenueCommand;
import site.awn.event.api.EventFacade;
import site.awn.event.api.EventView;
import site.awn.event.api.SessionView;
import site.awn.event.api.VenueView;
import site.awn.support.AbstractPostgresIntegrationTest;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 活动、场次、票档、商品模块集成测试（真实 PostgreSQL）。
 * 验收口径（todo Phase 02）：Flyway 迁移可从空库执行；JPA 映射 ddl-auto: validate 通过；实体级测试。
 */
@SpringBootTest
class CatalogModuleTests extends AbstractPostgresIntegrationTest {

    @Autowired EventFacade events;
    @Autowired CatalogFacade catalog;

    private String uid() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void venueEventSessionProductLifecycle() {
        String suffix = uid();

        VenueView venue = events.createVenue(new CreateVenueCommand(
                "v-" + suffix, "国家体育场", "北京", "朝阳区国家体育场南路1号"));
        assertThat(venue.venueCode()).isEqualTo("v-" + suffix);
        assertThat(venue.name()).isEqualTo("国家体育场");

        Instant start = Instant.now().plusSeconds(3600);
        Instant end = start.plusSeconds(7200);

        EventView event = events.createEvent(new CreateEventCommand(
                "e-" + suffix, "演唱会", "测试活动", start, end, venue.id()));
        assertThat(event.status()).isEqualTo(site.awn.event.domain.EventStatus.DRAFT);
        assertThat(event.venueId()).isEqualTo(venue.id());

        SessionView session = events.createSession(new CreateSessionCommand(
                "s-" + suffix, event.id(), "第一场", start, end));
        assertThat(session.status()).isEqualTo(site.awn.event.domain.SessionStatus.DRAFT);

        List<SessionView> sessions = events.sessions(event.id());
        assertThat(sessions).hasSize(1);
        assertThat(sessions.get(0).sessionCode()).isEqualTo("s-" + suffix);

        TicketCategoryView category = catalog.createCategory(new CreateCategoryCommand(
                "c-" + suffix, "VIP票", "VIP区域"));
        assertThat(category.name()).isEqualTo("VIP票");

        ProductView product = catalog.createProduct(new CreateProductCommand(
                "p-" + suffix, event.id(), session.id(), category.id(),
                "VIP票·第一场", 199900L));
        assertThat(product.status()).isEqualTo(ProductStatus.DRAFT);
        assertThat(product.unitPrice()).isEqualTo(199900L);

        List<ProductView> products = catalog.products(event.id());
        assertThat(products).hasSize(1);
        assertThat(products.get(0).productCode()).isEqualTo("p-" + suffix);

        ProductView fetched = catalog.product("p-" + suffix);
        assertThat(fetched.name()).isEqualTo("VIP票·第一场");
        assertThat(fetched.sessionId()).isEqualTo(session.id());
    }

    @Test
    void publishedProductsBySessionFiltersCorrectly() {
        String suffix = uid();

        VenueView venue = events.createVenue(new CreateVenueCommand(
                "v-" + suffix, "场馆", "上海", "浦东"));
        Instant start = Instant.now().plusSeconds(3600);
        Instant end = start.plusSeconds(7200);
        EventView event = events.createEvent(new CreateEventCommand(
                "e-" + suffix, "音乐节", null, start, end, venue.id()));
        SessionView session = events.createSession(new CreateSessionCommand(
                "s-" + suffix, event.id(), "日场", start, end));
        TicketCategoryView category = catalog.createCategory(new CreateCategoryCommand(
                "c-" + suffix, "普通票", null));

        catalog.createProduct(new CreateProductCommand(
                "p1-" + suffix, event.id(), session.id(), category.id(),
                "普通票·日场", 59900L));

        List<ProductView> published = catalog.publishedProductsBySession(session.id());
        assertThat(published).isEmpty();
    }

    @Test
    void duplicateCodesAreRejected() {
        String suffix = uid();
        String code = "dup-" + suffix;

        events.createVenue(new CreateVenueCommand(code, "场馆A", null, null));
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                events.createVenue(new CreateVenueCommand(code, "场馆B", null, null)))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}
