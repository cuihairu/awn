package site.awn.fulfillment.api;

/** 履约单视图 */
public record FulfillmentView(String fulfillmentNo, String orderNo, String status, String failReason) {
}
