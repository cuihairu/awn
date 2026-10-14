package site.awn.event.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "session")
public class Session {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String sessionCode;

    private Long eventId;

    private String name;

    private Instant startTime;

    private Instant endTime;

    @Enumerated(EnumType.STRING)
    private SessionStatus status = SessionStatus.DRAFT;

    /** 场次级场馆覆盖（attach/detach）；null = 继承父活动场馆 */
    private Long venueId;

    private String city;

    private String address;

    private Double latitude;

    private Double longitude;

    @Column(insertable = false, updatable = false)
    private Instant createdAt;

    @Column(insertable = false, updatable = false)
    private Instant updatedAt;

    public Session() {
    }

    public Session(String sessionCode, Long eventId, String name,
                   Instant startTime, Instant endTime) {
        this.sessionCode = sessionCode;
        this.eventId = eventId;
        this.name = name;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public Long getId() {
        return id;
    }

    public String getSessionCode() {
        return sessionCode;
    }

    public void setSessionCode(String sessionCode) {
        this.sessionCode = sessionCode;
    }

    public Long getEventId() {
        return eventId;
    }

    public void setEventId(Long eventId) {
        this.eventId = eventId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public void setStartTime(Instant startTime) {
        this.startTime = startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public void setEndTime(Instant endTime) {
        this.endTime = endTime;
    }

    public SessionStatus getStatus() {
        return status;
    }

    public void setStatus(SessionStatus status) {
        this.status = status;
    }

    public Long getVenueId() {
        return venueId;
    }

    public void setVenueId(Long venueId) {
        this.venueId = venueId;
    }

    public String getCity() {
        return city;
    }

    public String getAddress() {
        return address;
    }

    public Double getLatitude() {
        return latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    /**
     * 写入场次地理位置（docs/design/ui-system.md §6.1）。
     *
     * 坐标成对出现：只给一个经纬度视为非法——半截坐标在地图上必然指错位置，
     * 比没有坐标更糟；范围越界同理。城市与地址可单独缺失（前端按未定位降级）。
     */
    public void applyGeo(String city, String address, Double latitude, Double longitude) {
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
        this.city = city;
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
