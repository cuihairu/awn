package site.awn.order.api;

/**
 * 订单行命令。快照字段（商品名、票档）在 Phase 02 商品 API 落地前
 * 由调用方传入，落地后改为服务端从目录读取，防止客户端伪造快照。
 *
 * <p>一行恰有两种形态（docs/architecture/trading-model.md G2）：
 * <ul>
 *   <li>数量型：{@code poolCode} 必填，向库存池预留 {@code quantity} 张；</li>
 *   <li>凭证型：{@code listingNo} 必填且 {@code quantity} 必须为 1，
 *       向挂牌的独立票品（1:1）预留，不触碰库存池。</li>
 * </ul>
 * 两种形态互斥：{@code poolCode} 与 {@code listingNo} 同时给出或同时缺失都拒绝，
 * 数据库 CHECK {@code ck_order_item_source} 兜底。
 *
 * @param poolCode           库存池（数量型行必填，凭证型行为 null）
 * @param listingNo          挂牌号（凭证型行必填，数量型行为 null）
 * @param productId          商品
 * @param productNameSnapshot 商品名快照
 * @param sessionId          场次（可选）
 * @param ticketCategorySnapshot 票档快照（可选）
 * @param quantity           数量，必须 &gt; 0；凭证型行必须为 1
 * @param unitPrice          单价（最小货币单位），必须 &gt;= 0
 * @param discountAmount     行优惠（最小货币单位），必须 &gt;= 0
 * @param feeAmount          行费用（最小货币单位），必须 &gt;= 0
 */
public record OrderLineCommand(
        String poolCode,
        String listingNo,
        String productId,
        String productNameSnapshot,
        String sessionId,
        String ticketCategorySnapshot,
        int quantity,
        long unitPrice,
        long discountAmount,
        long feeAmount) {

    /** 数量型行的便捷构造：不指定挂牌号。 */
    public OrderLineCommand(String poolCode, String productId, String productNameSnapshot,
                            String sessionId, String ticketCategorySnapshot, int quantity,
                            long unitPrice, long discountAmount, long feeAmount) {
        this(poolCode, null, productId, productNameSnapshot, sessionId,
                ticketCategorySnapshot, quantity, unitPrice, discountAmount, feeAmount);
    }

    public OrderLineCommand {
        boolean quantityLine = poolCode != null && !poolCode.isBlank();
        boolean voucherLine = listingNo != null && !listingNo.isBlank();
        if (quantityLine == voucherLine) {
            throw new IllegalArgumentException(
                    "exactly one of poolCode or listingNo is required");
        }
        if (voucherLine && quantity != 1) {
            throw new IllegalArgumentException("voucher line quantity must be 1");
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

    /** 是否凭证型行（挂牌票品；1:1，不触碰库存池） */
    public boolean voucherLine() {
        return listingNo != null && !listingNo.isBlank();
    }

    public long lineTotal() {
        return (long) quantity * unitPrice - discountAmount + feeAmount;
    }
}
