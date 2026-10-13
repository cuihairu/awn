package site.awn.catalog.api;

import java.util.List;

/**
 * 商品与票档的对外接口。
 */
public interface CatalogFacade {

    TicketCategoryView createCategory(CreateCategoryCommand command);

    TicketCategoryView category(String categoryCode);

    ProductView createProduct(CreateProductCommand command);

    ProductView publishProduct(String productCode);

    ProductView product(String productCode);

    List<ProductView> products(Long eventId);

    List<ProductView> publishedProductsBySession(Long sessionId);
}
