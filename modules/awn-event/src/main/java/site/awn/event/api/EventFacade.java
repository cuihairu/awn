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

    SessionView createSession(CreateSessionCommand command);

    List<SessionView> sessions(Long eventId);
}
