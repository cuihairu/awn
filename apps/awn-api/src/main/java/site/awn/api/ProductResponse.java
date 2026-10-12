package site.awn.api;

import site.awn.catalog.domain.ProductStatus;

/** 公共商品详情响应（GET /api/v1/products/{productId}）。 */
public record ProductResponse(
        String productId,
        String name,
        ProductStatus status,
        Long unitPrice,
        Long categoryId,
        Long sessionId
) {
}
