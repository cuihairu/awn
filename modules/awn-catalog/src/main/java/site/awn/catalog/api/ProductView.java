package site.awn.catalog.api;

import site.awn.catalog.domain.ProductStatus;

public record ProductView(Long id, String productCode, Long eventId, Long sessionId,
                   Long categoryId, String name, ProductStatus status, Long unitPrice) {
}
