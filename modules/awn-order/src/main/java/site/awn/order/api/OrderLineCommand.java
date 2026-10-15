package site.awn.order.api;

import java.util.List;

/**
 * 订单行命令。快照字段（商品名、票档）在 Phase 02 商品 API 落地前
 * 由调用方传入，落地后改为服务端从目录读取，防止客户端伪造快照。
 *
 * <p>一行恰有三种形态（docs/architecture/trading-model.md G2 / 关系要点 3）：
 * <ul>
 *   <li>数量型：{@code poolCode} 必填，向库存池预留 {@code quantity} 张；</li>
 *   <li>凭证型：{@code listingNo} 必填且 {@code quantity} 必须为 1，
 *       向挂牌的独立票品（1:1）预留，不触碰库存池；</li>
 *   <li>座位型：{@code seatNo} 必填且 {@code quantity} 必须为 1，
 *       向平台座位（platform_seats，1:1）预留，不触碰库存池与挂牌。</li>
 * </ul>
 * 三种形态互斥：同时给出多于一条路径或全部缺失都拒绝，
 * 数据库 CHECK {@code ck_order_item_source} 兜底。
 *
 * @param poolCode           库存池（数量型行必填，其余形态为 null）
 * @param listingNo          挂牌号（凭证型行必填，其余形态为 null）
 * @param seatNo             座位号（座位型行必填，其余形态为 null）
 * @param productId          商品
 * @param productNameSnapshot 商品名快照
 * @param sessionId          场次（可选）
 * @param ticketCategorySnapshot 票档快照（可选）
 * @param quantity           数量，必须 &gt; 0；凭证型 / 座位型行必须为 1
 * @param unitPrice          单价（最小货币单位），必须 &gt;= 0
 * @param discountAmount     行优惠（最小货币单位），必须 &gt;= 0
 * @param feeAmount          行费用（最小货币单位），必须 &gt;= 0
 * @param attendees          观演人实名（可选，一票一证：提供时数量必须等于 quantity）
 */
public record OrderLineCommand(
        String poolCode,
        String listingNo,
        String seatNo,
        String productId,
        String productNameSnapshot,
        String sessionId,
        String ticketCategorySnapshot,
        int quantity,
        long unitPrice,
        long discountAmount,
        long feeAmount,
        List<AttendeeCommand> attendees) {

    /** 数量型行的便捷构造：不指定挂牌号与座位号，不带实名。 */
    public OrderLineCommand(String poolCode, String productId, String productNameSnapshot,
                            String sessionId, String ticketCategorySnapshot, int quantity,
                            long unitPrice, long discountAmount, long feeAmount) {
        this(poolCode, null, null, productId, productNameSnapshot, sessionId,
                ticketCategorySnapshot, quantity, unitPrice, discountAmount, feeAmount, null);
    }

    /** 数量型行的便捷构造：带实名（一票一证，attendees 数量必须等于 quantity）。 */
    public OrderLineCommand(String poolCode, String productId, String productNameSnapshot,
                            String sessionId, String ticketCategorySnapshot, int quantity,
                            long unitPrice, long discountAmount, long feeAmount,
                            List<AttendeeCommand> attendees) {
        this(poolCode, null, null, productId, productNameSnapshot, sessionId,
                ticketCategorySnapshot, quantity, unitPrice, discountAmount, feeAmount, attendees);
    }

    /** 凭证型 / 数量型通吃的全参构造（兼容既有 10 参调用点，座位号与实名均为 null）。 */
    public OrderLineCommand(String poolCode, String listingNo, String productId,
                            String productNameSnapshot, String sessionId,
                            String ticketCategorySnapshot, int quantity,
                            long unitPrice, long discountAmount, long feeAmount) {
        this(poolCode, listingNo, null, productId, productNameSnapshot, sessionId,
                ticketCategorySnapshot, quantity, unitPrice, discountAmount, feeAmount, null);
    }

    /** 兼容既有 11 参调用点（不带实名）。 */
    public OrderLineCommand(String poolCode, String listingNo, String seatNo, String productId,
                            String productNameSnapshot, String sessionId,
                            String ticketCategorySnapshot, int quantity,
                            long unitPrice, long discountAmount, long feeAmount) {
        this(poolCode, listingNo, seatNo, productId, productNameSnapshot, sessionId,
                ticketCategorySnapshot, quantity, unitPrice, discountAmount, feeAmount, null);
    }

    public OrderLineCommand {
        boolean quantityLine = poolCode != null && !poolCode.isBlank();
        boolean voucherLine = listingNo != null && !listingNo.isBlank();
        boolean seatLine = seatNo != null && !seatNo.isBlank();
        int sources = (quantityLine ? 1 : 0) + (voucherLine ? 1 : 0) + (seatLine ? 1 : 0);
        if (sources != 1) {
            throw new IllegalArgumentException(
                    "exactly one of poolCode, listingNo or seatNo is required");
        }
        if (voucherLine && quantity != 1) {
            throw new IllegalArgumentException("voucher line quantity must be 1");
        }
        if (seatLine && quantity != 1) {
            throw new IllegalArgumentException("seat line quantity must be 1");
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
        // 实名一票一证：提供 attendees 时逐票对应（数量必须与 quantity 一致）
        if (attendees != null) {
            if (attendees.size() != quantity) {
                throw new IllegalArgumentException(
                        "attendee count must match quantity: " + attendees.size() + " != " + quantity);
            }
            attendees = List.copyOf(attendees);
        }
    }

    /** 座位型行工厂：一行一座（1:1），不触碰库存池与挂牌 */
    public static OrderLineCommand seatLine(String seatNo, String productId,
                                            String productNameSnapshot, String sessionId,
                                            String ticketCategorySnapshot, long unitPrice,
                                            long discountAmount, long feeAmount) {
        return new OrderLineCommand(null, null, seatNo, productId, productNameSnapshot,
                sessionId, ticketCategorySnapshot, 1, unitPrice, discountAmount, feeAmount);
    }

    /** 是否凭证型行（挂牌票品；1:1，不触碰库存池） */
    public boolean voucherLine() {
        return listingNo != null && !listingNo.isBlank();
    }

    /** 是否座位型行（平台座位；1:1，不触碰库存池与挂牌） */
    public boolean seatLine() {
        return seatNo != null && !seatNo.isBlank();
    }

    public long lineTotal() {
        return (long) quantity * unitPrice - discountAmount + feeAmount;
    }
}
