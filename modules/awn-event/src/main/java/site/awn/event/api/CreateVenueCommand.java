package site.awn.event.api;

public record CreateVenueCommand(String venueCode, String name, String city, String address) {
}
