package site.awn.event.domain;

/**
 * 场次不存在（映射契约错误码 SESSION_NOT_FOUND）。
 */
public class SessionNotFoundException extends RuntimeException {

    public SessionNotFoundException(Long sessionId) {
        super("session not found: " + sessionId);
    }
}
