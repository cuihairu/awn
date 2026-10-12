package site.awn.payment.domain;

/** 支付单不存在。 */
public class PaymentNotFoundException extends RuntimeException {

    public PaymentNotFoundException(String paymentNo) {
        super("payment not found: " + paymentNo);
    }
}
