package site.awn.marketplace.api;

/** 卖家视图 */
public record SellerView(String sellerNo, Long userId, String displayName, String contact, String status) {
}
