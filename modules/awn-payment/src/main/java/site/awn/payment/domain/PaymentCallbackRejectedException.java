package site.awn.payment.domain;

/** 回调验签失败：拒绝处理（docs/architecture/payment-state-machine.md 回调验签）。 */
public class PaymentCallbackRejectedException extends RuntimeException {

    public PaymentCallbackRejectedException(String message) {
        super(message);
    }
}
