package site.awn.event.api;

import java.time.Instant;

public record CreateEventCommand(String eventCode, String name, String description,
                           Instant startTime, Instant endTime, Long venueId) {
}
