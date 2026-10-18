package site.awn.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletRequest;
import site.awn.api.auth.AuthenticatedUser;
import site.awn.fulfillment.api.FulfillmentFacade;
import site.awn.fulfillment.api.FulfillmentView;
import site.awn.order.api.OrderFacade;
import site.awn.order.api.OrderLineCommand;
import site.awn.order.api.QuoteCommand;
import site.awn.order.api.QuoteView;

import java.util.List;

/**
 * 结算接口（组合根）：报价与履约查询。
 *
 * <p>报价只是结算预览：按订单行同一服务端口径计算金额，
 * 不预留库存、不产生订单；下单时金额重新计算并逐行原子预留。
 * 履约查询按订单号取履约单；订单未知或未进入履约 → 404。
 */
@RestController
public class CheckoutController {

    private final OrderFacade orders;
    private final FulfillmentFacade fulfillments;

    public CheckoutController(OrderFacade orders, FulfillmentFacade fulfillments) {
        this.orders = orders;
        this.fulfillments = fulfillments;
    }

    /** 结算报价：不扣减任何库存；非法行（负行合计 / 双来源 / 非法数量）→ 400 */
    @PostMapping("/api/v1/checkout/quote")
    public QuoteView quote(@RequestBody QuoteRequest request) {
        List<OrderLineCommand> lines = request.lines().stream()
                .map(l -> new OrderLineCommand(l.poolCode(), l.listingNo(), l.productId(),
                        l.productNameSnapshot(), l.sessionId(), l.ticketCategorySnapshot(),
                        l.quantity(), l.unitPrice(), l.discountAmount(), l.feeAmount()))
                .toList();
        return orders.quote(new QuoteCommand(request.currency(), lines));
    }

    /** 订单履约单查询；无履约单（订单未知 / 未支付进入履约）→ 404 统一错误信封 */
    @GetMapping("/api/v1/orders/{orderId}/fulfillment")
    public FulfillmentView fulfillment(@PathVariable String orderId, HttpServletRequest request) {
        AuthenticatedUser.requireOwner(orders.ownerOf(orderId), AuthenticatedUser.userId(request));
        return fulfillments.fulfillmentOf(orderId);
    }
}
