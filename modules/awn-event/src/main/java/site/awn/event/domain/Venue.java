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

    @Column(insertable = false, updatable = false)
    private Instant createdAt;

    @Column(insertable = false, updatable = false)
    private Instant updatedAt;

    public Venue() {
    }

    public Venue(String venueCode, String name, String city, String address) {
        this(venueCode, name, city, address, null, null);
    }

    /**
     * 场馆地理（V19）：城市与地址可单独缺失（资料未录全是正常态），
     * 坐标必须成对出现且在合法范围——半截坐标在地图上必然指错位置，比没有更糟。
     */
    public Venue(String venueCode, String name, String city, String address,
                 Double latitude, Double longitude) {
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
        this.venueCode = venueCode;
        this.name = name;
        this.city = city;
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
