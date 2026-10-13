package site.awn.event.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.awn.event.api.CreateEventCommand;
import site.awn.event.api.CreateSessionCommand;
import site.awn.event.api.CreateVenueCommand;
import site.awn.event.api.EventFacade;
import site.awn.event.api.EventView;
import site.awn.event.api.SessionView;
import site.awn.event.api.VenueView;
import site.awn.event.domain.Event;
import site.awn.event.domain.EventNotFoundException;
import site.awn.event.domain.EventStatus;
import site.awn.event.domain.Session;
import site.awn.event.domain.SessionNotFoundException;
import site.awn.event.domain.SessionStatus;
import site.awn.event.domain.Venue;
import site.awn.event.infrastructure.EventRepository;
import site.awn.event.infrastructure.SessionRepository;
import site.awn.event.infrastructure.VenueRepository;

import java.util.List;

@Service
public class EventFacadeImpl implements EventFacade {

    /** 对公开读可见的活动状态 */
    private static final List<EventStatus> PUBLIC_STATUSES =
            List.of(EventStatus.PUBLISHED, EventStatus.COMPLETED);

    private final VenueRepository venues;
    private final EventRepository events;
    private final SessionRepository sessions;

    public EventFacadeImpl(VenueRepository venues,
                           EventRepository events,
                           SessionRepository sessions) {
        this.venues = venues;
        this.events = events;
        this.sessions = sessions;
    }

    @Override
    @Transactional
    public VenueView createVenue(CreateVenueCommand command) {
        Venue venue = new Venue(command.venueCode(), command.name(),
                command.city(), command.address());
        return toView(venues.save(venue));
    }

    @Override
    @Transactional(readOnly = true)
    public VenueView venue(String venueCode) {
        return venues.findByVenueCode(venueCode)
                .map(this::toView)
                .orElseThrow(() -> new IllegalStateException("venue not found: " + venueCode));
    }

    @Override
    @Transactional
    public EventView createEvent(CreateEventCommand command) {
        Event event = new Event(command.eventCode(), command.name(),
                command.description(), command.startTime(), command.endTime(),
                command.venueId());
        event.setStatus(EventStatus.DRAFT);
        return toView(events.save(event));
    }

    @Override
    @Transactional(readOnly = true)
    public EventView event(String eventCode) {
        return events.findByEventCode(eventCode)
                .map(this::toView)
                .orElseThrow(() -> new EventNotFoundException(eventCode));
    }

    @Override
    @Transactional(readOnly = true)
    public List<EventView> events() {
        return events.findByStatusInOrderByStartTimeDesc(PUBLIC_STATUSES).stream()
                .map(this::toView)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public EventView eventById(Long eventId) {
        return events.findById(eventId)
                .filter(Event::isPublic)
                .map(this::toView)
                .orElseThrow(() -> new EventNotFoundException(eventId));
    }

    @Override
    @Transactional
    public EventView publishEvent(String eventCode) {
        Event event = events.findByEventCode(eventCode)
                .orElseThrow(() -> new EventNotFoundException(eventCode));
        event.publish();
        return toView(event);
    }

    @Override
    @Transactional
    public SessionView createSession(CreateSessionCommand command) {
        Session session = new Session(command.sessionCode(), command.eventId(),
                command.name(), command.startTime(), command.endTime());
        session.setStatus(SessionStatus.DRAFT);
        return toView(sessions.save(session));
    }

    @Override
    @Transactional(readOnly = true)
    public SessionView session(Long sessionId) {
        return sessions.findById(sessionId)
                .map(this::toView)
                .orElseThrow(() -> new SessionNotFoundException(sessionId));
    }

    /**
     * 公开场次详情：场次不存在、或父活动未对公众公开（未发布）一律 404。
     * 与 {@link #session(Long)} 分开：场次商品列表等既有链路按场次直接取数，
     * 不因活动未发布而整体不可用。
     */
    @Override
    @Transactional(readOnly = true)
    public SessionView publicSession(Long sessionId) {
        return sessions.findById(sessionId)
                .filter(this::publiclyVisible)
                .map(this::toView)
                .orElseThrow(() -> new SessionNotFoundException(sessionId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<SessionView> sessions(Long eventId) {
        return sessions.findByEventId(eventId).stream()
                .map(this::toView)
                .toList();
    }

    /** 场次仅在其父活动对公开读可见时可见 */
    private boolean publiclyVisible(Session session) {
        return events.findById(session.getEventId())
                .map(Event::isPublic)
                .orElse(false);
    }

    private VenueView toView(Venue v) {
        return VenueView.of(v.getId(), v.getVenueCode(), v.getName(),
                v.getCity(), v.getAddress());
    }

    private EventView toView(Event e) {
        return new EventView(e.getId(), e.getEventCode(), e.getName(),
                e.getStatus(), e.getStartTime(), e.getEndTime(), e.getVenueId());
    }

    private SessionView toView(Session s) {
        return new SessionView(s.getId(), s.getSessionCode(), s.getEventId(),
                s.getName(), s.getStatus(), s.getStartTime(), s.getEndTime());
    }
}
