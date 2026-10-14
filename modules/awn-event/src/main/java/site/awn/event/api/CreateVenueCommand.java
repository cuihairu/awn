package site.awn.event.api;

/**
 * 创建场馆请求：城市与地址可缺省（场馆资料未录全），
 * 坐标缺省即「暂无坐标」，前端按占位渲染，不以 (0,0) 兜底。
 */
public record CreateVenueCommand(String venueCode, String name, String city, String address,
                                 Double latitude, Double longitude) {

    public CreateVenueCommand(String venueCode, String name, String city, String address) {
        this(venueCode, name, city, address, null, null);
    }
}