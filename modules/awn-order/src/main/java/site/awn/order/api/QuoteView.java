package site.awn.order.api;

/** 报价视图：金额为最小货币单位整数，服务端计算；不代表任何库存占用。 */
public record QuoteView(
        String currency,
        long totalAmount,
        long discountAmount,
        long feeAmount,
        long payAmount) {
}
