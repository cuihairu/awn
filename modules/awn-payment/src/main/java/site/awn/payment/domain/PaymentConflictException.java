package site.awn.payment.domain;

/** 支付状态冲突：非法迁移、金额不一致、重复渠道交易号等。 */
public class PaymentConflictException extends RuntimeException {

    public PaymentConflictException(String message) {
        super(message);
    }
}
