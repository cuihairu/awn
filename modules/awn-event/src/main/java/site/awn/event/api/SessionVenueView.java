package site.awn.event.api;

/**
 * 场次地理位置视图（docs/design/ui-system.md §6.1）。
 *
 * <p>坐标缺失是正常态（场馆信息未录全）：latitude / longitude 为 null 时前端渲染占位，
 * 不按 (0,0) 处理。场次可见性由父活动决定，地理信息不改变可见性口径。
 */
public record SessionVenueView(
        String venueCode,
        String venueName,
        String city,
        String address,
        Double latitude,
        Double longitude) {

    public static SessionVenueView of(String venueCode, String venueName, String city,
                                      String address, Double latitude, Double longitude) {
        return new SessionVenueView(venueCode, venueName, city, address, latitude, longitude);
    }
}