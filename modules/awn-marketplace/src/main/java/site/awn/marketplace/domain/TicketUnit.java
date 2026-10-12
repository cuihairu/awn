package site.awn.marketplace.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 独立票品：唯一编号的票，状态与挂牌解耦。
 * 挂牌下架不影响票品状态；票品作废也不隐式改变挂牌。
 */
@Entity
@Table(name = "ticket_units")
public class TicketUnit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "unit_no", nullable = false, unique = true)
    private String unitNo;

    @Column(name = "listing_id", nullable = false)
    private Long listingId;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TicketUnitStatus status;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    protected TicketUnit() {
    }

    public TicketUnit(String unitNo, Long listingId, Long sellerId) {
        this.unitNo = unitNo;
        this.listingId = listingId;
        this.sellerId = sellerId;
        this.status = TicketUnitStatus.IN_STOCK;
    }

    public void transitionTo(TicketUnitStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new MarketplaceConflictException(
                    "ticket unit transition not allowed: " + status + " -> " + target);
        }
        this.status = target;
    }

    public Long getId() {
        return id;
    }

    public String getUnitNo() {
        return unitNo;
    }

    public Long getListingId() {
        return listingId;
    }

    public Long getSellerId() {
        return sellerId;
    }

    public TicketUnitStatus getStatus() {
        return status;
    }
}
