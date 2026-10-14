package site.awn.api;

import site.awn.inventory.api.SeatView;
import site.awn.inventory.domain.SeatStatus;

/**
 * 公开座位图条目（GET /api/v1/sessions/{sessionId}/seatmap）。
 * 只透出编号与状态：orderNo（占座订单号）与 sessionCode 不进公开面；
 * productCode 保留，客户端据此把座位档与票档卡对应。
 */
public record SeatMapItem(
        String seatNo,
        String productCode,
        String section,
        String row,
        String seat,
        SeatStatus status
) {

    public static SeatMapItem of(SeatView seat) {
        return new SeatMapItem(seat.seatNo(), seat.productCode(), seat.section(),
                seat.row(), seat.seat(), seat.status());
    }
}
