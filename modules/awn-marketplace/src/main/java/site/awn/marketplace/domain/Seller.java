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

/** 卖家资料。供货审核与挂牌都挂在卖家名下。 */
@Entity
@Table(name = "sellers")
public class Seller {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seller_no", nullable = false, unique = true)
    private String sellerNo;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "contact")
    private String contact;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SellerStatus status;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    protected Seller() {
    }

    public Seller(String sellerNo, Long userId, String displayName, String contact) {
        this.sellerNo = sellerNo;
        this.userId = userId;
        this.displayName = displayName;
        this.contact = contact;
        this.status = SellerStatus.ACTIVE;
    }

    public boolean isActive() {
        return status == SellerStatus.ACTIVE;
    }

    public void suspend() {
        if (status != SellerStatus.ACTIVE) {
            throw new IllegalStateException("suspend not allowed from status " + status);
        }
        this.status = SellerStatus.SUSPENDED;
    }

    public Long getId() {
        return id;
    }

    public String getSellerNo() {
        return sellerNo;
    }

    public Long getUserId() {
        return userId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getContact() {
        return contact;
    }

    public SellerStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
