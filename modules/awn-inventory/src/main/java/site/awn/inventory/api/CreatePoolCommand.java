package site.awn.inventory.api;

public record CreatePoolCommand(
        String poolCode,
        String productId,
        String sessionId,
        int totalQuantity
) {
}
