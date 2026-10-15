package site.awn.event.api;

/**
 * 场馆地理与地图配置更新（V26）：全量覆盖，字段为 null 表示清空。
 *
 * <p>mapImageUrl 是运营提供的「位置示意图」（非精确地图，受控存储域白名单）；
 * 地图标记为图上百分比（0-100），成对出现——半截标记必然指错位置，比没有更糟。
 * 坐标守卫与创建同口径（成对 + 范围）。
 */
public record UpdateVenueCommand(String name, String city, String address,
                                 Double latitude, Double longitude,
                                 String mapImageUrl, Double mapMarkerX, Double mapMarkerY) {
}
