package site.awn.event.domain;

/**
 * 演出不存在（映射契约错误码 EVENT_NOT_FOUND）。
 */
public class EventNotFoundException extends RuntimeException {

    public EventNotFoundException(String eventCode) {
        super("event not found: " + eventCode);
    }
}
