package site.awn.marketplace.api;

/** 挂牌（来自已提交的供货审核） */
public record CreateListingCommand(String sellerNo, String reviewNo, String sessionCode,
                                   long unitPrice, boolean platformGuaranteed) {
}
