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
import java.util.UUID;

/**
 * 测试用真实商品链工厂：createPool 经 catalog api 校验商品编码存在（G1），
 * 库存/订单/支付等集成测试的池必须挂在真实商品上。
 * 测试库从不截断：全链编码追加随机段，重复运行不撞唯一键。
 */
@Component
public class CatalogProductFactory {

    private final EventFacade events;
    private final CatalogFacade catalog;

    public CatalogProductFactory(EventFacade events, CatalogFacade catalog) {
        this.events = events;
        this.catalog = catalog;
    }

    /** 建 场馆→活动→场次→票档→商品 全链，返回商品编码；suffix 只用于可读性 */
    public String productCode(String suffix) {
        String code = suffix + "-" + UUID.randomUUID().toString().substring(0, 8);
        var venue = events.createVenue(new CreateVenueCommand(
                "v-" + code, "测试场馆", "北京", null));
        Instant start = Instant.now().plusSeconds(3600);
        Instant end = start.plusSeconds(7200);
        var event = events.createEvent(new CreateEventCommand(
                "e-" + code, "测试活动", null, start, end, venue.id()));
        var session = events.createSession(new CreateSessionCommand(
                "s-" + code, event.id(), "第一场", start, end));
        var category = catalog.createCategory(new CreateCategoryCommand(
                "c-" + code, "普通票", null));
        var product = catalog.createProduct(new CreateProductCommand(
                "p-" + code, event.id(), session.id(), category.id(),
                "测试商品", 10000L));
        return product.productCode();
    }
}
