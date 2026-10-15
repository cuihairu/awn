package site.awn.event.api;

/**
 * 场馆视图。城市 / 地址 / 坐标都可缺省（资料未录全是正常态）。
 * mapImageUrl 是「位置示意图」（V26，方案 B 首方静态图），标记为图上百分比。
 */
public record VenueView(Long id, String venueCode, String name, String city, String address,
                        Double latitude, Double longitude,
                        String mapImageUrl, Double mapMarkerX, Double mapMarkerY) {

    public static VenueView of(Long id, String venueCode, String name, String city,
                               String address, Double latitude, Double longitude,
                               String mapImageUrl, Double mapMarkerX, Double mapMarkerY) {
        return new VenueView(id, venueCode, name, city, address, latitude, longitude,
                mapImageUrl, mapMarkerX, mapMarkerY);
    }

    /** 场次地理视图：城市 / 地址 / 坐标同口径透出 */
    public SessionVenueView toSessionVenue() {
        return SessionVenueView.of(venueCode, name, city, address, latitude, longitude,
                mapImageUrl, mapMarkerX, mapMarkerY);
    }
}
