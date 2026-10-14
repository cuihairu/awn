package site.awn.event.api;

import site.awn.event.domain.SessionStatus;

import java.time.Instant;

/**
 * 场次视图。geo 为场馆派生（场次场馆优先，缺省继承活动场馆），可能是 null
 * （场馆信息未录全是正常态，不按未知场馆兜底造数据）。
 */
public record SessionView(Long id, String sessionCode, Long eventId, String name,
                   SessionStatus status, Instant startTime, Instant endTime,
                   SessionVenueView geo) {

    /** 模块内部装配（暂不带地理）：运营接口按编码读写场次时地理可不参与 */
    public static SessionView of(Long id, String sessionCode, Long eventId, String name,
                                 SessionStatus status, Instant startTime, Instant endTime) {
        return new SessionView(id, sessionCode, eventId, name, status, startTime, endTime, null);
    }

    public SessionView withGeo(SessionVenueView venue) {
        return new SessionView(id, sessionCode, eventId, name, status, startTime, endTime, venue);
    }
}