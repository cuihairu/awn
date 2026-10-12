package site.awn.event.api;

import java.util.Optional;

public record VenueView(Long id, String venueCode, String name, String city, String address) {

    public static VenueView of(Long id, String venueCode, String name, String city, String address) {
        return new VenueView(id, venueCode, name, city, address);
    }
}
