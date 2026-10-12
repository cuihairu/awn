package site.awn.order.domain;

/**
 * 幂等键冲突：同一幂等键对应了不同的请求内容。
 * 独立于状态机冲突，以便映射到专用错误码 IDEMPOTENCY_CONFLICT。
 */
public class IdempotencyConflictException extends OrderConflictException {

    public IdempotencyConflictException(String message) {
        super(message);
    }
}
