package site.awn.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import site.awn.catalog.api.CatalogFacade;
import site.awn.catalog.api.ProductView;
import site.awn.event.api.EventFacade;
import site.awn.inventory.api.BalanceView;
import site.awn.inventory.api.InventoryFacade;

import java.util.List;

/**
 * 公共商品接口（组合根）：聚合 catalog、event 与 inventory 的模块接口。
 * 可售数量为缓存展示口径；下单链路另行原子预留重校验。
 */
@RestController
public class PublicProductController {

    private final CatalogFacade catalog;
    private final EventFacade events;
    private final InventoryFacade inventory;

    public PublicProductController(CatalogFacade catalog, EventFacade events,
                                   InventoryFacade inventory) {
        this.catalog = catalog;
        this.events = events;
        this.inventory = inventory;
    }

    @GetMapping("/api/v1/products/{productId}")
    public ProductResponse product(@PathVariable String productId) {
        ProductView product = catalog.product(productId);
        return new ProductResponse(product.productCode(), product.name(), product.status(),
                product.unitPrice(), product.categoryId(), product.sessionId());
    }

    @GetMapping("/api/v1/products/{productId}/availability")
    public AvailabilityResponse availability(@PathVariable String productId) {
        ProductView product = catalog.product(productId);
        return inventory.balanceOfProduct(productId)
                .map(balance -> AvailabilityResponse.of(product.status(), productId,
                        balance.totalQuantity(), balance.available()))
                .orElseGet(() -> AvailabilityResponse.of(product.status(), productId));
    }

    /**
     * 场次商品列表：仅已发布商品，按创建顺序返回；available 为库存展示口径（无池时缺省）。
     * 场次不存在 → 404（SESSION_NOT_FOUND）。
     */
    @GetMapping("/api/v1/sessions/{sessionId}/products")
    public List<ProductListItem> sessionProducts(@PathVariable Long sessionId) {
        events.session(sessionId);
        return catalog.publishedProductsBySession(sessionId).stream()
                .map(p -> new ProductListItem(p.productCode(), p.name(), p.status(),
                        p.unitPrice(), p.categoryId(), p.sessionId(),
                        inventory.balanceOfProduct(p.productCode())
                                .map(BalanceView::available).orElse(null)))
                .toList();
    }
}
