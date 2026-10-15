package site.awn.order.api;

/**
 * 卖家申辩命令（docs/architecture/dispute-handling.md「卖家面」）：
 * 声明卖家 + 申辩内容；归属校验（声明卖家 = 争议快照 seller_id）在应用层裁决。
 */
public record SubmitSellerResponseCommand(String disputeNo, String sellerNo, String content) {
}
