package site.awn.order.api;

import java.time.Instant;

/**
 * 卖家申辩视图（docs/architecture/dispute-handling.md「卖家面」）：
 * 只走管理面读取入口，不进买家面订单争议列表响应。
 */
public record DisputeSellerResponseView(String disputeNo, String sellerNo, String content,
                                        Instant createdAt) {
}
