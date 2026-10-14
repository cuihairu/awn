package site.awn.event.domain;

/**
 * 场馆不存在（映射契约错误码 VENUE_NOT_FOUND）。
 */
public class VenueNotFoundException extends RuntimeException {

    public VenueNotFoundException(String venueCode) {
        super("venue not found: " + venueCode);
    }
}