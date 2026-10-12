package site.awn.api;

import site.awn.catalog.domain.ProductStatus;

/**
 * 公共商品可售摘要响应（GET /api/v1/products/{productId}/availability）。
 * 数量为库存余额的缓存展示口径，下单时后端仍会原子预留重校验。
 * 商品尚未建池时 sellable 为 false、数量为 null（未开售）。
 */
public record AvailabilityResponse(
        String productId,
        ProductStatus status,
        Boolean sellable,
        Integer total,
        Integer available
) {

    public static AvailabilityResponse of(ProductStatus status, String productId) {
        return new AvailabilityResponse(productId, status, false, null, null);
    }

    public static AvailabilityResponse of(ProductStatus status, String productId,
                                          int total, int available) {
        boolean sellable = available > 0 && status == ProductStatus.PUBLISHED;
        return new AvailabilityResponse(productId, status, sellable, total, available);
    }
}
