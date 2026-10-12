package site.awn.marketplace.api;

/** 供货审核视图 */
public record SupplyReviewView(String reviewNo, String sellerNo, String productCode, String sessionCode,
                               int quantity, long unitPrice, String status, String reviewer,
                               String rejectReason) {
}
