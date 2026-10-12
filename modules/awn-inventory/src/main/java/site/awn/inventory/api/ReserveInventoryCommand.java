package site.awn.inventory.api;

import java.time.Duration;

/**
 * 预留命令。orderRef 由订单链路传入（Phase 03）；ttl 为空表示不设超时。
 */
public record ReserveInventoryCommand(
        String poolCode,
        String orderRef,
        int quantity,
        Duration ttl
) {

    public ReserveInventoryCommand {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
    }
}
