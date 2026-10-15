package site.awn.event.api;

/**
 * 场次地理位置视图（docs/design/ui-system.md §6.1）。
 *
 * <p>坐标缺失是正常态（场馆信息未录全）：latitude / longitude 为 null 时前端渲染占位，
 * 不按 (0,0) 处理。场次可见性由父活动决定，地理信息不改变可见性口径。
 *
 * <p>mapImageUrl 是运营提供的「位置示意图」（V26，方案 B 首方静态图）：只作大致位置提示，
 * 不承诺地理精度，前端须明示示意图口径；地图标记是图上百分比（0-100），可与图片单独缺失。
 */
public record SessionVenueView(
        String venueCode,
        String venueName,
        String city,
        String address,
        Double latitude,
        Double longitude,
        String mapImageUrl,
        Double mapMarkerX,
        Double mapMarkerY) {

    public static SessionVenueView of(String venueCode, String venueName, String city,
                                      String address, Double latitude, Double longitude,
                                      String mapImageUrl, Double mapMarkerX, Double mapMarkerY) {
        return new SessionVenueView(venueCode, venueName, city, address, latitude, longitude,
                mapImageUrl, mapMarkerX, mapMarkerY);
    }
}
