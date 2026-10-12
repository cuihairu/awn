package site.awn.campaign.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 专场引用的活动及排序。
 */
@Entity
@Table(name = "campaign_events")
public class CampaignEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "campaign_id", nullable = false)
    private Long campaignId;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected CampaignEvent() {
    }

    public CampaignEvent(Long campaignId, Long eventId, Integer displayOrder) {
        this.campaignId = campaignId;
        this.eventId = eventId;
        this.displayOrder = displayOrder;
    }

    public Long getId() {
        return id;
    }

    public Long getCampaignId() {
        return campaignId;
    }

    public Long getEventId() {
        return eventId;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
