package site.awn.event.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "venue")
public class Venue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String venueCode;

    private String name;

    private String city;

    private String address;

    private Double latitude;

    private Double longitude;

    /** 位置示意图（V26）：运营提供的静态图，非精确地图；域白名单在应用层校验 */
    private String mapImageUrl;

    /** 图上标记位置（百分比 0-100，相对图片左上角），与 mapImageUrl 无关可单独缺失 */
    @Column(name = "map_marker_x")
    private Double mapMarkerX;

    @Column(name = "map_marker_y")
    private Double mapMarkerY;

    @Column(insertable = false, updatable = false)
    private Instant createdAt;

    @Column(insertable = false, updatable = false)
    private Instant updatedAt;

    public Venue() {
    }

    public Venue(String venueCode, String name, String city, String address) {
        this(venueCode, name, city, address, null, null, null, null, null);
    }

    /**
     * 场馆地理（V19）：城市与地址可单独缺失（资料未录全是正常态），
     * 坐标必须成对出现且在合法范围——半截坐标在地图上必然指错位置，比没有更糟。
     */
    public Venue(String venueCode, String name, String city, String address,
                 Double latitude, Double longitude) {
        this(venueCode, name, city, address, latitude, longitude, null, null, null);
    }

    /** 地图配置（V26）：坐标与标记同一「成对 + 范围」守卫，半截一律拒 */
    public Venue(String venueCode, String name, String city, String address,
                 Double latitude, Double longitude, String mapImageUrl,
                 Double mapMarkerX, Double mapMarkerY) {
        this.venueCode = venueCode;
        this.mapImageUrl = mapImageUrl;
        applyGeo(name, city, address, latitude, longitude);
        applyMapMarker(mapMarkerX, mapMarkerY);
    }

    public Long getId() {
        return id;
    }

    public String getVenueCode() {
        return venueCode;
    }

    public void setVenueCode(String venueCode) {
        this.venueCode = venueCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public Double getLatitude() {
        return latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public String getMapImageUrl() {
        return mapImageUrl;
    }

    public Double getMapMarkerX() {
        return mapMarkerX;
    }

    public Double getMapMarkerY() {
        return mapMarkerY;
    }

    /**
     * 运营更新地理与地图配置（V26）：坐标成对 + 范围守卫（同构造口径），
     * 标记成对 + 百分比范围守卫；mapImageUrl 的域白名单在应用层校验后传入。
     */
    public void applyGeo(String name, String city, String address, Double latitude, Double longitude) {
        boolean hasLat = latitude != null;
        boolean hasLng = longitude != null;
        if (hasLat != hasLng) {
            throw new IllegalArgumentException("latitude and longitude must be provided together");
        }
        if (hasLat && (latitude < -90 || latitude > 90)) {
            throw new IllegalArgumentException("latitude must be between -90 and 90: " + latitude);
        }
        if (hasLng && (longitude < -180 || longitude > 180)) {
            throw new IllegalArgumentException("longitude must be between -180 and 180: " + longitude);
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("venue name is required");
        }
        this.name = name;
        this.city = city;
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    /** 图上标记（百分比 0-100）：成对出现，半截标记必然指错位置，比没有更糟 */
    public void applyMapMarker(Double mapMarkerX, Double mapMarkerY) {
        boolean hasX = mapMarkerX != null;
        boolean hasY = mapMarkerY != null;
        if (hasX != hasY) {
            throw new IllegalArgumentException("map marker x and y must be provided together");
        }
        if (hasX && (mapMarkerX < 0 || mapMarkerX > 100)) {
            throw new IllegalArgumentException("map marker x must be between 0 and 100: " + mapMarkerX);
        }
        if (hasY && (mapMarkerY < 0 || mapMarkerY > 100)) {
            throw new IllegalArgumentException("map marker y must be between 0 and 100: " + mapMarkerY);
        }
        this.mapMarkerX = mapMarkerX;
        this.mapMarkerY = mapMarkerY;
    }

    public void setMapImageUrl(String mapImageUrl) {
        this.mapImageUrl = mapImageUrl;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
