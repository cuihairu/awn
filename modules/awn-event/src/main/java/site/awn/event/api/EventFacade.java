package site.awn.event.api;

import java.util.List;

/**
 * 活动、场次、场馆的对外接口。
 */
public interface EventFacade {

    VenueView createVenue(CreateVenueCommand command);

    VenueView venue(String venueCode);

    EventView createEvent(CreateEventCommand command);

    EventView event(String eventCode);

    /** 公开活动列表：仅已发布 / 已结束，按开始时间倒序 */
    List<EventView> events();

    /** 运营活动列表：全状态（含 DRAFT），按 id 升序（运营台发布流用） */
    List<EventView> allEvents();

    /** 公开活动详情：未知或非公开状态一律 404 */
    EventView eventById(Long eventId);

    /** 发布活动（DRAFT → PUBLISHED），非草稿状态抛 EventConflictException */
    EventView publishEvent(String eventCode);

    SessionView createSession(CreateSessionCommand command);

    SessionView session(Long sessionId);

    /** 公开场次详情：场次不存在或父活动未公开 → SessionNotFoundException */
    SessionView publicSession(Long sessionId);

    /** 公开场次列表：父活动未公开 → EventNotFoundException；按开始时间升序 */
    List<SessionView> publicSessions(Long eventId);

    List<SessionView> sessions(Long eventId);
}
