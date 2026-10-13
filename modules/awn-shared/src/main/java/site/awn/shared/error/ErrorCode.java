package site.awn.shared.error;

/**
 * 统一错误码（docs/api/contracts.md「错误信封与错误码」）。
 * 错误码与 HTTP 状态的映射由后端唯一维护，各端不得自行推断。
 */
public enum ErrorCode {

    VALIDATION_FAILED(400),
    NOT_FOUND(404),
    ORDER_NOT_FOUND(404),
    INVENTORY_POOL_NOT_FOUND(404),
    EVENT_NOT_FOUND(404),
    CAMPAIGN_NOT_FOUND(404),
    PAYMENT_NOT_FOUND(404),
    REFUND_NOT_FOUND(404),
    ORDER_CONFLICT(409),
    IDEMPOTENCY_CONFLICT(409),
    INSUFFICIENT_INVENTORY(409),
    INVENTORY_CONFLICT(409),
    PAYMENT_CONFLICT(409),
    PAYMENT_CALLBACK_REJECTED(400),
    REFUND_CONFLICT(409),
    CONFLICT(409),
    RATE_LIMITED(429),
    INTERNAL_ERROR(500);

    private final int httpStatus;

    ErrorCode(int httpStatus) {
        this.httpStatus = httpStatus;
    }

    public int httpStatus() {
        return httpStatus;
    }
}
