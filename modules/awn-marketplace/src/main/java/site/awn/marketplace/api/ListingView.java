package site.awn.marketplace.api;

/** 挂牌视图 */
public record ListingView(String listingNo, String sellerNo, String productCode, String sessionCode,
                          long unitPrice, boolean platformGuaranteed, String status) {
}
