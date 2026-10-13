package site.awn.api;

import site.awn.catalog.domain.ProductStatus;

/**
 * 场次商品列表项响应（GET /api/v1/sessions/{sessionId}/products）。
 * 只含已发布商品，按创建顺序返回；available 为库存展示口径（无池时缺省，即未开售），
 * 下单时后端仍会原子预留重校验。
 */
public record ProductListItem(
        String productCode,
        String name,
        ProductStatus status,
        Long unitPrice,
        Long categoryId,
        Long sessionId,
        Integer available
) {
}
