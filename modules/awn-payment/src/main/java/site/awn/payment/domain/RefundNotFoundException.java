package site.awn.payment.domain;

/** 退款单不存在。 */
public class RefundNotFoundException extends RuntimeException {

    public RefundNotFoundException(String refundNo) {
        super("refund not found: " + refundNo);
    }
}
