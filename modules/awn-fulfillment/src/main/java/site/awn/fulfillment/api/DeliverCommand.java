package site.awn.fulfillment.api;

/** 卖家交付：按订单号定位履约单 */
public record DeliverCommand(String orderNo, String evidenceUrl, String note) {
}
