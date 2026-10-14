package site.awn.event.api;

import java.util.List;

/**
 * 活动、场次、场馆的对外接口。
 */
public interface EventFacade {

    VenueView createVenue(CreateVenueCommand command);

    VenueView venue(String venueCode);

    /** 运营场馆列表（按 id 升序）：场次配置按场馆挑选 */
    List<VenueView> venues();

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

    /** 运营场次列表：父活动不存在 → EventNotFoundException；按开始时间升序 */
    List<SessionView> allSessions(String eventCode);

    /**
     * 场次地理位置（docs/design/ui-system.md §6.1）：场次指定场馆优先，
     * 未指定时继承父活动场馆；两者都没有则为空（场馆信息不全是正常态）。
     */
    SessionVenueView sessionVenue(String sessionCode);

    /** 给场次指定场馆（巡回赛每场换城市）；场次或场馆未知一律 404 */
    SessionVenueView attachSessionVenue(String sessionCode, String venueCode);

    /** 解除场次场馆覆盖，回到继承父活动场馆 */
    SessionVenueView detachSessionVenue(String sessionCode);
}