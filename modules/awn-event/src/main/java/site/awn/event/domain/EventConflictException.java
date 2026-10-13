package site.awn.event.domain;

/**
 * 活动状态机冲突（映射契约错误码 CONFLICT）。
 */
public class EventConflictException extends RuntimeException {

    public EventConflictException(String message) {
        super(message);
    }
}
