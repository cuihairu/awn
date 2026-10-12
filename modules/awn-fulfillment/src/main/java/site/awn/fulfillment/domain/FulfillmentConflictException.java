package site.awn.fulfillment.domain;

/** 履约状态机非法迁移 / 状态冲突 */
public class FulfillmentConflictException extends RuntimeException {

    public FulfillmentConflictException(String message) {
        super(message);
    }
}
