package site.awn.order.domain;

/** 幂等键冲突（同键不同请求）或状态机冲突。对应 409 语义。 */
public class OrderConflictException extends RuntimeException {

    public OrderConflictException(String message) {
        super(message);
    }
}
