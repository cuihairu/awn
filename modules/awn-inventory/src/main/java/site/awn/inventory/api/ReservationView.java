package site.awn.inventory.api;

import java.time.Instant;

public record ReservationView(
        String reservationNo,
        String poolCode,
        int quantity,
        Instant expiresAt
) {
}
