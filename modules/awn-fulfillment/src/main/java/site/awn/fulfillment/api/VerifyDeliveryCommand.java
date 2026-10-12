package site.awn.fulfillment.api;

/** 平台核验交付 */
public record VerifyDeliveryCommand(String orderNo, boolean passed, String verifier, String note) {
}
