package site.awn.marketplace.api;

/** 注册卖家 */
public record CreateSellerCommand(Long userId, String displayName, String contact) {
}
