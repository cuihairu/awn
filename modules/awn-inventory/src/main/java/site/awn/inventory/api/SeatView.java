package site.awn.inventory.api;

import site.awn.inventory.domain.SeatStatus;

/**
 * 平台座位视图。orderNo 为当前占座订单（预留 / 已售时非空，其余为 null）。
 * status 为逐座状态机：IN_STOCK / RESERVED / SOLD / DELIVERED / INVALID。
 * x / y 为可选座位坐标（S2 契约先行，V27 pos_x/pos_y）：null = 坐标缺失，
 * 前端回退现有网格渲染；坐标写入路径随数据管线落地，此前恒为 null。
 */
public record SeatView(
        String seatNo,
        String productCode,
        String sessionCode,
        String section,
        String row,
        String seat,
        String orderNo,
        SeatStatus status,
        Double x,
        Double y
) {
}
