package site.awn.order.domain;

/** 候补席位状态机冲突（映射契约错误码 WAITLIST_CONFLICT）。 */
public class WaitlistConflictException extends RuntimeException {

    public WaitlistConflictException(String message) {
        super(message);
    }
}