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
@Table(name = "event")
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String eventCode;

    private String name;

    private String description;

    @Enumerated(EnumType.STRING)
    private EventStatus status = EventStatus.DRAFT;

    private Instant startTime;

    private Instant endTime;

    private Long venueId;

    @Column(insertable = false, updatable = false)
    private Instant createdAt;

    @Column(insertable = false, updatable = false)
    private Instant updatedAt;

    public Event() {
    }

    public Event(String eventCode, String name, String description,
                 Instant startTime, Instant endTime, Long venueId) {
        this.eventCode = eventCode;
        this.name = name;
        this.description = description;
        this.startTime = startTime;
        this.endTime = endTime;
        this.venueId = venueId;
    }

    public Long getId() {
        return id;
    }

    /**
     * 发布守卫：只有草稿活动可发布；已发布 / 已结束 / 已取消一律拒绝。
     * 公开读端点（GET /api/v1/events、/sessions、/intelligence/events/{eventId}/prices）
     * 只暴露已发布或已结束的活动，发布是唯一让活动进入公开链路的途径。
     */
    public void publish() {
        if (status != EventStatus.DRAFT) {
            throw new EventConflictException(
                    "publish not allowed from status " + status + ": " + eventCode);
        }
        this.status = EventStatus.PUBLISHED;
    }

    /** 是否对公开读可见（已发布或已结束） */
    public boolean isPublic() {
        return status == EventStatus.PUBLISHED || status == EventStatus.COMPLETED;
    }

    public String getEventCode() {
        return eventCode;
    }

    public void setEventCode(String eventCode) {
        this.eventCode = eventCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public EventStatus getStatus() {
        return status;
    }

    public void setStatus(EventStatus status) {
        this.status = status;
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

    public Long getVenueId() {
        return venueId;
    }

    public void setVenueId(Long venueId) {
        this.venueId = venueId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
