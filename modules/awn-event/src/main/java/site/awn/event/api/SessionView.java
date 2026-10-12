package site.awn.event.api;

import site.awn.event.domain.SessionStatus;

import java.time.Instant;

public record SessionView(Long id, String sessionCode, Long eventId, String name,
                   SessionStatus status, Instant startTime, Instant endTime) {
}
