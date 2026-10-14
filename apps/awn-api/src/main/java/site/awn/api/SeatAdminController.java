package site.awn.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import site.awn.inventory.api.CreateSeatsCommand;
import site.awn.inventory.api.InventoryFacade;
import site.awn.inventory.api.SeatView;

import java.util.List;

/**
 * 铺座运营入口（管理面，todo 2D 座位图余项）：
 * 一次铺一个商品在同一场次下的座位（商品经 catalog 校验，同场次同位置唯一）。
 * 鉴权由 AdminAuthFilter 统一执行（/api/v1/admin/** X-Admin-Token）。
 * GET 为运营视图：含占座订单号（公开座位图剥去该字段）。
 */
@RestController
@RequestMapping("/api/v1/admin/sessions/{sessionCode}/seats")
public class SeatAdminController {

    private final InventoryFacade inventory;

    public SeatAdminController(InventoryFacade inventory) {
        this.inventory = inventory;
    }

    /** 铺座：位置列表成批创建，全部成功或整体失败（重复位置 / 未知商品拒绝） */
    @PostMapping
    public ResponseEntity<List<SeatView>> createSeats(
            @PathVariable String sessionCode,
            @RequestBody CreateSeatsRequest request) {
        List<SeatView> seats = inventory.createSeats(new CreateSeatsCommand(
                request.productCode(), sessionCode, request.positions().stream()
                .map(p -> new CreateSeatsCommand.SeatPosition(p.section(), p.row(), p.seat()))
                .toList()));
        return ResponseEntity.status(HttpStatus.CREATED).body(seats);
    }

    /** 场次座位视图：运营核对位置与逐座状态（含占座订单号） */
    @GetMapping
    public List<SeatView> seats(@PathVariable String sessionCode) {
        return inventory.seatsOfSession(sessionCode);
    }

    public record SeatPositionRequest(String section, String row, String seat) {
    }

    public record CreateSeatsRequest(String productCode, List<SeatPositionRequest> positions) {
    }
}
