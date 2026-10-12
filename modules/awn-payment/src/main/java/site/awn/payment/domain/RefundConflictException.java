package site.awn.payment.domain;

/** 退款状态冲突：非法迁移、累计退款超支付金额等。 */
public class RefundConflictException extends RuntimeException {

    public RefundConflictException(String message) {
        super(message);
    }
}
