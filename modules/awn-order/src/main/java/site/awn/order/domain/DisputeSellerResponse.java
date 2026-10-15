package site.awn.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 卖家申辩（docs/architecture/dispute-handling.md「卖家面」）：
 * 追加留痕，不覆盖前一条——一条申辩一张记录，管理员裁决时能看到全过程。
 * 归属校验（声明卖家 = 争议快照 seller_id）与在途守卫在应用层裁决。
 */
@Entity
@Table(name = "order_dispute_seller_responses")
public class DisputeSellerResponse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dispute_no", nullable = false)
    private String disputeNo;

    /** 声明卖家（服务端校验与争议快照 seller_id 一致） */
    @Column(name = "seller_no", nullable = false)
    private String sellerNo;

    @Column(name = "content", nullable = false)
    private String content;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected DisputeSellerResponse() {
    }

    public static DisputeSellerResponse submit(String disputeNo, String sellerNo, String content) {
        return new DisputeSellerResponse(disputeNo, sellerNo, content);
    }

    private DisputeSellerResponse(String disputeNo, String sellerNo, String content) {
        this.disputeNo = disputeNo;
        this.sellerNo = sellerNo;
        this.content = content;
    }

    public Long getId() {
        return id;
    }

    public String getDisputeNo() {
        return disputeNo;
    }

    public String getSellerNo() {
        return sellerNo;
    }

    public String getContent() {
        return content;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
