package site.awn.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletRequest;
import site.awn.api.auth.AuthenticatedUser;
import site.awn.marketplace.api.MarketplaceFacade;
import site.awn.order.api.DisputeFacade;
import site.awn.order.api.DisputeSellerResponseView;
import site.awn.order.api.DisputeView;
import site.awn.order.api.OpenDisputeCommand;
import site.awn.order.api.SubmitSellerResponseCommand;
import site.awn.order.domain.DisputeReason;
import site.awn.order.domain.DisputeRequestType;

import java.net.URI;
import java.util.List;

/**
 * 争议买家面与卖家面（组合根，docs/architecture/dispute-handling.md）：
 * 买家开单 / 撤回 / 查自己订单的争议；卖家查归属争议 / 提交申辩。
 * 身份均为请求声明 + 服务端归属校验（交易面当前无认证中间件，认证接入后守卫逻辑不变）。
 * 受理与裁决在 /api/v1/admin/disputes/**（AdminAuthFilter 收口），买卖双方都没有裁决入口。
 */
@RestController
public class DisputeController {

    private final DisputeFacade disputes;
    private final MarketplaceFacade marketplace;

    public DisputeController(DisputeFacade disputes, MarketplaceFacade marketplace) {
        this.disputes = disputes;
        this.marketplace = marketplace;
    }

    @PostMapping("/api/v1/orders/{orderNo}/disputes")
    public ResponseEntity<DisputeView> open(
            @PathVariable String orderNo,
            @RequestBody OpenDisputeRequest request, HttpServletRequest httpRequest) {
        long userId = requireBuyer(httpRequest);
        DisputeView dispute = disputes.openDispute(new OpenDisputeCommand(
                orderNo, userId, request.reason(), request.description(),
                request.evidenceUrl(), request.requestedResolution(), request.requestedAmount()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(URI.create("/api/v1/disputes/" + dispute.disputeNo()))
                .body(dispute);
    }

    @GetMapping("/api/v1/orders/{orderNo}/disputes")
    public List<DisputeView> listForOrder(
            @PathVariable String orderNo,
            HttpServletRequest request) {
        long userId = requireBuyer(request);
        return disputes.disputesOfOrder(orderNo, userId);
    }

    @PostMapping("/api/v1/disputes/{disputeNo}/withdraw")
    public DisputeView withdraw(
            @PathVariable String disputeNo,
            @RequestBody(required = false) WithdrawDisputeRequest request, HttpServletRequest httpRequest) {
        return disputes.withdraw(disputeNo, requireBuyer(httpRequest));
    }

    /**
     * 卖家争议：先用 bearer token 校验 sellerNo 的账号归属，再按争议开单时快照读取。
     * 未知卖家 404，token 不属于该卖家 403。
     */
    @GetMapping("/api/v1/sellers/{sellerNo}/disputes")
    public List<DisputeView> listForSeller(@PathVariable String sellerNo, HttpServletRequest request) {
        requireSellerOwner(sellerNo, request);
        return disputes.disputesOfSeller(sellerNo);
    }

    /**
     * 卖家申辩：bearer token 必须拥有请求声明的 sellerNo；争议快照归属与在途守卫仍由模块层检查。
     * （非快照卖家 / 纯平台库存争议 / 已终态一律 409 DISPUTE_CONFLICT）。
     */
    @PostMapping("/api/v1/disputes/{disputeNo}/seller-response")
    public ResponseEntity<DisputeSellerResponseView> submitSellerResponse(
            @PathVariable String disputeNo,
            @RequestBody SubmitSellerResponseRequest request, HttpServletRequest httpRequest) {
        requireSellerOwner(request.sellerNo(), httpRequest);
        DisputeSellerResponseView response = disputes.submitSellerResponse(
                new SubmitSellerResponseCommand(disputeNo, request.sellerNo(), request.content()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(URI.create("/api/v1/disputes/" + disputeNo + "/seller-responses"))
                .body(response);
    }

    private long requireBuyer(HttpServletRequest request) {
        return AuthenticatedUser.userId(request);
    }

    private void requireSellerOwner(String sellerNo, HttpServletRequest request) {
        long userId = AuthenticatedUser.userId(request);
        AuthenticatedUser.requireOwner(marketplace.sellerOf(sellerNo).userId(), userId);
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

    public record SubmitSellerResponseRequest(String sellerNo, String content) {
    }
}
