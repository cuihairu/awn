package site.awn.marketplace.api;

/** 提交供货审核申请 */
public record SubmitSupplyReviewCommand(String sellerNo, String productCode, String sessionCode,
                                        int quantity, long unitPrice, String evidenceUrl) {
}
