package site.awn.campaign.api;

/**
 * 添加专场商品引用命令。
 */
public record AddProductCommand(
        Long productId,
        Integer displayOrder
) {
}
