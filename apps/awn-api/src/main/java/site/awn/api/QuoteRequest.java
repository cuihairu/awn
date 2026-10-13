package site.awn.api;

import java.util.List;

/** 报价请求（POST /api/v1/checkout/quote）：行与创建订单同构，只做金额计算。 */
public record QuoteRequest(String currency, List<CreateOrderRequest.Line> lines) {
}
