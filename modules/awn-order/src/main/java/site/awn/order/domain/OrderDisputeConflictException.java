package site.awn.order.domain;

/** 争议单状态机与归属守卫冲突（映射契约错误码 DISPUTE_CONFLICT）。 */
public class OrderDisputeConflictException extends RuntimeException {

    public OrderDisputeConflictException(String message) {
        super(message);
    }
}
