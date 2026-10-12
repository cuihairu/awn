package site.awn.event.api;

import site.awn.event.domain.EventStatus;

import java.time.Instant;

public record EventView(Long id, String eventCode, String name, EventStatus status,
                 Instant startTime, Instant endTime, Long venueId) {
}
