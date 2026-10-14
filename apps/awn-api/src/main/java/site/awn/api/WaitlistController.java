package site.awn.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import site.awn.order.api.JoinWaitlistCommand;
import site.awn.order.api.WaitlistFacade;
import site.awn.order.api.WaitlistView;

import java.util.List;

/**
 * 排队 / 候补接口（契约「排队 / 候补」）。
 *
 * <p>排队只登记意愿：条目不占库存、不预生成订单；库存释放后由 Worker 递补任务
 * 按池 FIFO 生成待支付订单（与普通下单同源、同一金额与倒计时口径）。
 * 票档信息是加入候补时的快照（客户端按所选商品与场次上报，服务端按池与金额口径校验），
 * 排队期间商品改价不回溯历史条目。
 */
@RestController
public class WaitlistController {

    private final WaitlistFacade waitlist;

    public WaitlistController(WaitlistFacade waitlist) {
        this.waitlist = waitlist;
    }

    /** 加入候补；同用户同池已占用席位 → 409 WAITLIST_CONFLICT，未知池 → 404 */
    @PostMapping("/api/v1/waitlist")
    public ResponseEntity<WaitlistView> join(@RequestBody JoinWaitlistRequest request) {
        WaitlistView entry = waitlist.join(new JoinWaitlistCommand(
                request.sessionId(), request.poolCode(), request.productId(), request.productName(),
                request.quantity(), request.unitPrice(), request.currency(), request.userId()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(java.net.URI.create("/api/v1/waitlist/" + entry.waitlistNo()))
                .body(entry);
    }

    /** 候补条目；未知编号 → 404 WAITLIST_NOT_FOUND */
    @GetMapping("/api/v1/waitlist/{waitlistNo}")
    public WaitlistView waitlist(@PathVariable String waitlistNo) {
        return waitlist.waitlist(waitlistNo);
    }

    /** 某用户的候补列表（加入时间倒序）；缺 userId → 400 */
    @GetMapping("/api/v1/waitlist")
    public List<WaitlistView> mine(@RequestParam(required = false) Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId is required");
        }
        return waitlist.waitlistOf(userId);
    }

    /** 退出排队；重复取消幂等返回 {@code canceled: false}，已递补或已终态 → 409 */
    @PostMapping("/api/v1/waitlist/{waitlistNo}/cancel")
    public WaitlistCancelResult cancel(@PathVariable String waitlistNo) {
        return new WaitlistCancelResult(waitlist.cancel(waitlistNo));
    }

    public record JoinWaitlistRequest(
            String sessionId,
            String poolCode,
            String productId,
            String productName,
            int quantity,
            long unitPrice,
            String currency,
            Long userId) {
    }

    public record WaitlistCancelResult(boolean canceled) {
    }
}
