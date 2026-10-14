package site.awn.marketplace.api;

/**
 * 挂牌视图。{@code sellerId} 是市场卖家主键（争议单开单时按它做卖家归属快照），
 * {@code sellerNo} 是对外的卖家编号。
 */
public record ListingView(String listingNo, Long sellerId, String sellerNo, String productCode,
                          String sessionCode, long unitPrice, boolean platformGuaranteed,
                          String status) {
}
