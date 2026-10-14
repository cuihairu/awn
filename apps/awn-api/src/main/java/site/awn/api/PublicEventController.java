package site.awn.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import site.awn.event.api.EventFacade;
import site.awn.event.api.EventView;
import site.awn.event.api.SessionView;

import java.util.List;

/**
 * 公共活动 / 场次接口（组合根）：只读，且只暴露对公众可见的内容。
 *
 * <p>活动仅「已发布 / 已结束」可读，场次仅在其父活动公开时可读；
 * 未知或未发布的活动与场次一律 404（EVENT_NOT_FOUND / SESSION_NOT_FOUND），
 * 避免把草稿内容泄露到公开面。
 */
@RestController
public class PublicEventController {

    private final EventFacade events;

    public PublicEventController(EventFacade events) {
        this.events = events;
    }

    /** 公开活动列表：已发布 / 已结束，按开始时间倒序 */
    @GetMapping("/api/v1/events")
    public List<EventView> events() {
        return events.events();
    }

    /** 公开活动详情；未知或非公开状态 → 404 */
    @GetMapping("/api/v1/events/{eventId}")
    public EventView event(@PathVariable Long eventId) {
        return events.eventById(eventId);
    }

    /** 公开场次详情；场次不存在或父活动未公开 → 404 */
    @GetMapping("/api/v1/sessions/{sessionId}")
    public SessionView session(@PathVariable Long sessionId) {
        return events.publicSession(sessionId);
    }

    /** 公开场次列表（活动页选场次）；活动未知或未公开 → 404 */
    @GetMapping("/api/v1/events/{eventId}/sessions")
    public List<SessionView> sessions(@PathVariable Long eventId) {
        return events.publicSessions(eventId);
    }
}
