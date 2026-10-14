package site.awn.event.api;

/**
 * 场馆视图。城市 / 地址 / 坐标都可缺省（资料未录全是正常态）。
 */
public record VenueView(Long id, String venueCode, String name, String city, String address,
                        Double latitude, Double longitude) {

    public static VenueView of(Long id, String venueCode, String name, String city,
                               String address, Double latitude, Double longitude) {
        return new VenueView(id, venueCode, name, city, address, latitude, longitude);
    }

    /** 场次地理视图：城市 / 地址 / 坐标同口径透出 */
    public SessionVenueView toSessionVenue() {
        return SessionVenueView.of(venueCode, name, city, address, latitude, longitude);
    }
}