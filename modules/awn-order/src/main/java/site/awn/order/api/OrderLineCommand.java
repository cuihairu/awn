package site.awn.order.api;

/**
 * 订单行命令。快照字段（商品名、票档）在 Phase 02 商品 API 落地前
 * 由调用方传入，落地后改为服务端从目录读取，防止客户端伪造快照。
 *
 * @param poolCode           库存池
 * @param productId          商品
 * @param productNameSnapshot 商品名快照
 * @param sessionId          场次（可选）
 * @param ticketCategorySnapshot 票档快照（可选）
 * @param quantity           数量，必须 &gt; 0
 * @param unitPrice          单价（最小货币单位），必须 &gt;= 0
 * @param discountAmount     行优惠（最小货币单位），必须 &gt;= 0
 * @param feeAmount          行费用（最小货币单位），必须 &gt;= 0
 */
public record OrderLineCommand(
        String poolCode,
        String productId,
        String productNameSnapshot,
        String sessionId,
        String ticketCategorySnapshot,
        int quantity,
        long unitPrice,
        long discountAmount,
        long feeAmount) {

    public OrderLineCommand {
        if (poolCode == null || poolCode.isBlank()) {
            throw new IllegalArgumentException("poolCode is required");
        }
        if (productId == null || productId.isBlank()) {
            throw new IllegalArgumentException("productId is required");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        if (unitPrice < 0 || discountAmount < 0 || feeAmount < 0) {
            throw new IllegalArgumentException("amounts must be non-negative");
        }
    }

    public long lineTotal() {
        return (long) quantity * unitPrice - discountAmount + feeAmount;
    }
}
