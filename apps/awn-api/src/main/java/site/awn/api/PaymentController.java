package site.awn.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import site.awn.order.api.OrderFacade;
import site.awn.order.api.OrderView;
import site.awn.payment.api.CreatePaymentCommand;
import site.awn.payment.api.PaymentFacade;
import site.awn.payment.api.PaymentView;
import site.awn.payment.api.RefundView;
import site.awn.payment.api.RequestRefundCommand;

import java.util.List;

/**
 * 交易接口（组合根）：订单的支付与退款。
 *
 * <p>金额不可由前端决定：创建支付单时金额 / 币种一律取订单快照
 * （{@code OrderView.payAmount} / {@code currency}），客户端只选择渠道；
 * 支付成功的确认只来自渠道回调（模块层验签），任何端不得凭参数确认支付。
 * 退款要求 {@code Idempotency-Key} 请求头，与模块层退款幂等键同一语义；
 * 退款单必须挂在属于该订单的支付单上，跨订单支付单一律 404。
 */
@RestController
public class PaymentController {

    private final OrderFacade orders;
    private final PaymentFacade payments;

    public PaymentController(OrderFacade orders, PaymentFacade payments) {
        this.orders = orders;
        this.payments = payments;
    }

    /** 为订单发起支付：金额/币种服务端定，客户端只选渠道；订单不存在 → 404 */
    @PostMapping("/api/v1/orders/{orderId}/payments")
    public ResponseEntity<PaymentView> createPayment(
            @PathVariable String orderId,
            @RequestBody CreatePaymentRequest request) {
        OrderView order = orders.orderOf(orderId);
        PaymentView payment = payments.createPayment(new CreatePaymentCommand(
                order.orderNo(), request.channel(), order.currency(), order.payAmount()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(java.net.URI.create("/api/v1/orders/" + order.orderNo() + "/payments"))
                .body(payment);
    }

    /** 订单的支付历史（创建顺序）；订单不存在 → 404，未支付返回空列表 */
    @GetMapping("/api/v1/orders/{orderId}/payments")
    public List<PaymentView> payments(@PathVariable String orderId) {
        orders.orderOf(orderId);
        return payments.paymentsOfOrder(orderId);
    }

    /** 可用支付渠道清单（由已注册网关决定）；未知渠道发起支付一律 409 */
    @GetMapping("/api/v1/payments/channels")
    public List<String> channels() {
        return payments.channels();
    }

    /**
     * 失败单重试（可换渠道）：请求体 {@code {"channel":"..."}}，channel 省略时沿用原支付单渠道。
     * 守卫（契约「支付失败重试与换渠道」）：未知支付单 → 404；源单非失败态 / 订单不可支付 →
     * 409；金额与币种仍取订单应付快照，客户端传值不参与计算。
     */
    @PostMapping("/api/v1/payments/{paymentNo}/retry")
    public ResponseEntity<PaymentView> retryPayment(
            @PathVariable String paymentNo,
            @RequestBody(required = false) CreatePaymentRequest request) {
        PaymentView payment = payments.retryPayment(paymentNo, request == null ? null : request.channel());
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(java.net.URI.create("/api/v1/payments/" + payment.paymentNo()))
                .body(payment);
    }

    /**
     * 发起退款：幂等键缺失 → 400；支付单不属于该订单或不存在 → 404；
     * 支付未成功 / 超额退款 → 409（模块层状态机与额度守卫）。
     */
    @PostMapping("/api/v1/orders/{orderId}/refunds")
    public ResponseEntity<RefundView> requestRefund(
            @PathVariable String orderId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody RequestRefundRequest request) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key header is required");
        }
        orders.orderOf(orderId);
        // 支付单必须属于该订单：跨订单退款一律按不存在处理
        PaymentView payment = payments.payment(request.paymentNo());
        if (!payment.orderNo().equals(orderId)) {
            throw new site.awn.payment.domain.PaymentNotFoundException(request.paymentNo());
        }
        RefundView refund = payments.requestRefund(new RequestRefundCommand(
                request.paymentNo(), idempotencyKey, request.amount(), request.reason()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(java.net.URI.create("/api/v1/orders/" + orderId + "/refunds"))
                .body(refund);
    }
}
