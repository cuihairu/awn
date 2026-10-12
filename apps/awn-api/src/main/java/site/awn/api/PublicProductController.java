package site.awn.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import site.awn.catalog.api.CatalogFacade;
import site.awn.catalog.api.ProductView;
import site.awn.inventory.api.InventoryFacade;

/**
 * 公共商品接口（组合根）：聚合 catalog 与 inventory 的模块接口。
 * 可售数量为缓存展示口径；下单链路另行原子预留重校验。
 */
@RestController
public class PublicProductController {

    private final CatalogFacade catalog;
    private final InventoryFacade inventory;

    public PublicProductController(CatalogFacade catalog, InventoryFacade inventory) {
        this.catalog = catalog;
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
}
