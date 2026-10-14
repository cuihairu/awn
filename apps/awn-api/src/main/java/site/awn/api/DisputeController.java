package site.awn.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import site.awn.order.api.DisputeFacade;
import site.awn.order.api.DisputeView;
import site.awn.order.api.OpenDisputeCommand;
import site.awn.order.domain.DisputeReason;
import site.awn.order.domain.DisputeRequestType;

import java.net.URI;
import java.util.List;

/**
 * 争议买家面（组合根，docs/architecture/dispute-handling.md）：
 * 开单 / 撤回 / 查自己订单的争议。买家身份为请求声明 + 服务端订单归属校验
 * （交易面当前无认证中间件，认证接入后守卫逻辑不变）。
 * 受理与裁决在 /api/v1/admin/disputes/**（AdminAuthFilter 收口），买家面无任何裁决入口。
 */
@RestController
public class DisputeController {

    private final DisputeFacade disputes;

    public DisputeController(DisputeFacade disputes) {
        this.disputes = disputes;
    }

    @PostMapping("/api/v1/orders/{orderNo}/disputes")
    public ResponseEntity<DisputeView> open(
            @PathVariable String orderNo,
            @RequestBody OpenDisputeRequest request) {
        DisputeView dispute = disputes.openDispute(new OpenDisputeCommand(
                orderNo, request.userId(), request.reason(), request.description(),
                request.evidenceUrl(), request.requestedResolution(), request.requestedAmount()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(URI.create("/api/v1/disputes/" + dispute.disputeNo()))
                .body(dispute);
    }

    @GetMapping("/api/v1/orders/{orderNo}/disputes")
    public List<DisputeView> listForOrder(
            @PathVariable String orderNo,
            @RequestParam Long userId) {
        return disputes.disputesOfOrder(orderNo, userId);
    }

    @PostMapping("/api/v1/disputes/{disputeNo}/withdraw")
    public DisputeView withdraw(
            @PathVariable String disputeNo,
            @RequestBody WithdrawDisputeRequest request) {
        return disputes.withdraw(disputeNo, request.userId());
    }

    /**
     * 开争议请求：枚举由请求体直接绑定（未知值 400）；
     * REFUND 必填 requestedAmount（≤ 订单应付），REDELIVER 不得带金额。
     */
    public record OpenDisputeRequest(Long userId, DisputeReason reason, String description,
                                     String evidenceUrl, DisputeRequestType requestedResolution,
                                     Long requestedAmount) {
    }

    public record WithdrawDisputeRequest(Long userId) {
    }
}
