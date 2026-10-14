package site.awn.order.domain;

/** 候补条目不存在（映射契约错误码 WAITLIST_NOT_FOUND）。 */
public class WaitlistNotFoundException extends RuntimeException {

    public WaitlistNotFoundException(String waitlistNo) {
        super("waitlist entry not found: " + waitlistNo);
    }
}