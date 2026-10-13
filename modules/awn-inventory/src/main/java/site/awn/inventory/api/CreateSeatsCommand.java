package site.awn.inventory.api;

import java.util.List;

/**
 * 批量创建平台座位（管理操作）：一次铺一个商品在同一场次下的座位图。
 * 同一场次同一物理位置（区 / 排 / 号）只允许一个商品占用，重复即拒绝；
 * 商品编码经 catalog 校验存在（与建池同口径，编码写错不得静默建座）。
 */
public record CreateSeatsCommand(
        String productCode,
        String sessionCode,
        List<SeatPosition> positions
) {

    public record SeatPosition(
            String section,
            String row,
            String seat
    ) {
    }

    public CreateSeatsCommand {
        if (positions == null || positions.isEmpty()) {
            throw new IllegalArgumentException("positions must not be empty");
        }
        if (positions.stream().distinct().count() != positions.size()) {
            throw new IllegalArgumentException("duplicate seat position in request");
        }
        positions = List.copyOf(positions);
    }
}
