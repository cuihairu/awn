package site.awn.support;

import org.springframework.stereotype.Component;
import site.awn.catalog.api.CatalogFacade;
import site.awn.catalog.api.CreateCategoryCommand;
import site.awn.catalog.api.CreateProductCommand;
import site.awn.event.api.CreateEventCommand;
import site.awn.event.api.CreateSessionCommand;
import site.awn.event.api.CreateVenueCommand;
import site.awn.event.api.EventFacade;

import java.time.Instant;

/**
 * 测试用真实商品链工厂：createPool 经 catalog api 校验商品编码存在（G1），
 * Worker 任务集成测试的池必须挂在真实商品上。
 * （与 awn-api 测试侧同名类对应：两个应用的测试支撑目前各自独立。）
 */
@Component
public class CatalogProductFactory {

    private final EventFacade events;
    private final CatalogFacade catalog;

    public CatalogProductFactory(EventFacade events, CatalogFacade catalog) {
        this.events = events;
        this.catalog = catalog;
    }

    /** 建 场馆→活动→场次→票档→商品 全链，返回商品编码；suffix 只用于编码唯一性 */
    public String productCode(String suffix) {
        var venue = events.createVenue(new CreateVenueCommand(
                "v-" + suffix, "测试场馆", "北京", null));
        Instant start = Instant.now().plusSeconds(3600);
        Instant end = start.plusSeconds(7200);
        var event = events.createEvent(new CreateEventCommand(
                "e-" + suffix, "测试活动", null, start, end, venue.id()));
        var session = events.createSession(new CreateSessionCommand(
                "s-" + suffix, event.id(), "第一场", start, end));
        var category = catalog.createCategory(new CreateCategoryCommand(
                "c-" + suffix, "普通票", null));
        var product = catalog.createProduct(new CreateProductCommand(
                "p-" + suffix, event.id(), session.id(), category.id(),
                "测试商品", 10000L));
        return product.productCode();
    }
}
