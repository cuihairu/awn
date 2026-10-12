package site.awn.catalog.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.awn.catalog.api.CatalogFacade;
import site.awn.catalog.api.CreateCategoryCommand;
import site.awn.catalog.api.CreateProductCommand;
import site.awn.catalog.api.ProductView;
import site.awn.catalog.api.TicketCategoryView;
import site.awn.catalog.domain.Product;
import site.awn.catalog.domain.ProductStatus;
import site.awn.catalog.domain.TicketCategory;
import site.awn.catalog.infrastructure.ProductRepository;
import site.awn.catalog.infrastructure.TicketCategoryRepository;

import java.util.List;

@Service
public class CatalogFacadeImpl implements CatalogFacade {

    private final TicketCategoryRepository categories;
    private final ProductRepository products;

    public CatalogFacadeImpl(TicketCategoryRepository categories,
                             ProductRepository products) {
        this.categories = categories;
        this.products = products;
    }

    @Override
    @Transactional
    public TicketCategoryView createCategory(CreateCategoryCommand command) {
        TicketCategory cat = new TicketCategory(command.categoryCode(),
                command.name(), command.description());
        return toView(categories.save(cat));
    }

    @Override
    @Transactional(readOnly = true)
    public TicketCategoryView category(String categoryCode) {
        return categories.findByCategoryCode(categoryCode)
                .map(this::toView)
                .orElseThrow(() -> new IllegalStateException("category not found: " + categoryCode));
    }

    @Override
    @Transactional
    public ProductView createProduct(CreateProductCommand command) {
        Product product = new Product(command.productCode(), command.eventId(),
                command.sessionId(), command.categoryId(), command.name(),
                command.unitPrice());
        product.setStatus(ProductStatus.DRAFT);
        return toView(products.save(product));
    }

    @Override
    @Transactional(readOnly = true)
    public ProductView product(String productCode) {
        return products.findByProductCode(productCode)
                .map(this::toView)
                .orElseThrow(() -> new IllegalStateException("product not found: " + productCode));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductView> products(Long eventId) {
        return products.findByEventId(eventId).stream()
                .map(this::toView)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductView> publishedProductsBySession(Long sessionId) {
        return products.findPublishedBySession(sessionId, ProductStatus.PUBLISHED).stream()
                .map(this::toView)
                .toList();
    }

    private TicketCategoryView toView(TicketCategory c) {
        return new TicketCategoryView(c.getId(), c.getCategoryCode(),
                c.getName(), c.getDescription());
    }

    private ProductView toView(Product p) {
        return new ProductView(p.getId(), p.getProductCode(), p.getEventId(),
                p.getSessionId(), p.getCategoryId(), p.getName(),
                p.getStatus(), p.getUnitPrice());
    }
}
