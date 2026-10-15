package site.awn.order.api;

import java.util.List;

/**
 * 订单行视图。
 * 凭证型行（挂牌票品）的 {@code poolCode} / {@code reservationNo} 为 null，
 * {@code listingNo} / {@code unitNo} 有值；座位型行仅 {@code seatNo} 有值；
 * 数量型行 {@code seatNo} / {@code listingNo} / {@code unitNo} 均为 null。
 * {@code attendees} 为逐票实名（证件号脱敏，{@link AttendeeView}）；无实名订单为空列表。
 */
public record OrderItemView(
        String poolCode,
        String productId,
        String productNameSnapshot,
        String sessionId,
        String ticketCategorySnapshot,
        int quantity,
        long unitPrice,
        long discountAmount,
        long feeAmount,
        long lineTotal,
        String reservationNo,
        String listingNo,
        String unitNo,
        String seatNo,
        List<AttendeeView> attendees) {

    public OrderItemView {
        attendees = attendees == null ? List.of() : List.copyOf(attendees);
    }
}
