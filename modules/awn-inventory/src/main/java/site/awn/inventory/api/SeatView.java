package site.awn.inventory.api;

import site.awn.inventory.domain.SeatStatus;

/**
 * 平台座位视图。orderNo 为当前占座订单（预留 / 已售时非空，其余为 null）。
 * status 为逐座状态机：IN_STOCK / RESERVED / SOLD / DELIVERED / INVALID。
 */
public record SeatView(
        String seatNo,
        String productCode,
        String sessionCode,
        String section,
        String row,
        String seat,
        String orderNo,
        SeatStatus status
) {
}
