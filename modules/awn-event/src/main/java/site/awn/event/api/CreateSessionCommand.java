package site.awn.event.api;

import java.time.Instant;

public record CreateSessionCommand(String sessionCode, Long eventId, String name,
                            Instant startTime, Instant endTime) {
}
