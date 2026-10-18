package site.awn.api;

import site.awn.inventory.api.SeatView;
import site.awn.inventory.domain.SeatStatus;

/**
 * 公开座位图条目（GET /api/v1/sessions/{sessionId}/seatmap）。
 * 只透出编号、状态与可选坐标：orderNo（占座订单号）与 sessionCode 不进公开面；
 * productCode 保留，客户端据此把座位档与票档卡对应。
 * x / y 为可选座位坐标（S2 契约先行，seatmap-3d-plan.md §3）：null = 坐标缺失，
 * 前端回退现有网格渲染，不破坏 2D 契约。
 */
public record SeatMapItem(
        String seatNo,
        String productCode,
        String section,
        String row,
        String seat,
        SeatStatus status,
        Double x,
        Double y
) {

    public static SeatMapItem of(SeatView seat) {
        return new SeatMapItem(seat.seatNo(), seat.productCode(), seat.section(),
                seat.row(), seat.seat(), seat.status(), seat.x(), seat.y());
    }
}
