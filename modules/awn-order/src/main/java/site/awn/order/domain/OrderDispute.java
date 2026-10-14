package site.awn.order.domain;

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
 * 争议单（docs/architecture/dispute-handling.md）：
 * 买家按订单开单，卖家归属在开单时从凭证型订单行解析并快照，不随挂牌变动回写。
 * 受理（OPEN → UNDER_REVIEW）与裁决（UNDER_REVIEW → RESOLVED）是管理员动作；
 * 买家只能撤回尚未受理的争议。争议是订单旁路记录，不阻塞订单状态机。
 */
@Entity
@Table(name = "order_disputes")
public class OrderDispute {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dispute_no", nullable = false, unique = true)
    private String disputeNo;

    @Column(name = "order_no", nullable = false)
    private String orderNo;

    /** 开单人 = 订单买家（服务端校验归属，组合根不重复判断） */
    @Column(name = "opened_by", nullable = false)
    private Long openedBy;

    /** 凭证行挂牌卖家快照；纯平台库存行订单为 null（裁决对象是平台自身履约） */
    @Column(name = "seller_id")
    private Long sellerId;

    /** 争议针对的凭证行（listing_no / unit_no 成对；数量型行订单为 null） */
    @Column(name = "listing_no")
    private String listingNo;

    @Column(name = "unit_no")
    private String unitNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false)
    private DisputeReason reason;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "evidence_url")
    private String evidenceUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "requested_resolution", nullable = false)
    private DisputeRequestType requestedResolution;

    /** REFUND 必填（≤ 订单应付，服务端校验）；REDELIVER 必须为空 */
    @Column(name = "requested_amount")
    private Long requestedAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private DisputeStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "resolution")
    private DisputeResolution resolution;

    @Column(name = "resolution_note")
    private String resolutionNote;

    @Column(name = "decided_by")
    private String decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    protected OrderDispute() {
    }

    /** 争议单工厂：状态恒为 OPEN；语义守卫（订单归属 / 状态 / 金额）在应用层开单用例中裁决 */
    public static OrderDispute open(String disputeNo, String orderNo, Long openedBy, Long sellerId,
                                    String listingNo, String unitNo, DisputeReason reason,
                                    String description, String evidenceUrl,
                                    DisputeRequestType requestedResolution, Long requestedAmount) {
        return new OrderDispute(disputeNo, orderNo, openedBy, sellerId, listingNo, unitNo,
                reason, description, evidenceUrl, requestedResolution, requestedAmount);
    }

    private OrderDispute(String disputeNo, String orderNo, Long openedBy, Long sellerId,
                         String listingNo, String unitNo, DisputeReason reason, String description,
                         String evidenceUrl, DisputeRequestType requestedResolution, Long requestedAmount) {
        this.disputeNo = disputeNo;
        this.orderNo = orderNo;
        this.openedBy = openedBy;
        this.sellerId = sellerId;
        this.listingNo = listingNo;
        this.unitNo = unitNo;
        this.reason = reason;
        this.description = description;
        this.evidenceUrl = evidenceUrl;
        this.requestedResolution = requestedResolution;
        this.requestedAmount = requestedAmount;
        this.status = DisputeStatus.OPEN;
    }

    /** 买家撤回：仅尚未受理的争议可撤；已受理只能由平台裁决关闭 */
    public void withdraw() {
        requireTransition(DisputeStatus.WITHDRAWN);
        this.status = DisputeStatus.WITHDRAWN;
    }

    /** 平台受理：受理后买家不能单方面撤回 */
    public void accept() {
        requireTransition(DisputeStatus.UNDER_REVIEW);
        this.status = DisputeStatus.UNDER_REVIEW;
    }

    /** 平台裁决：结论与留痕一次落定，终态后拒绝一切迁移 */
    public void adjudicate(DisputeResolution resolution, String note, String decidedBy, Instant decidedAt) {
        requireTransition(DisputeStatus.RESOLVED);
        if (resolution == null) {
            throw new IllegalArgumentException("dispute resolution is required");
        }
        if (note == null || note.isBlank()) {
            throw new IllegalArgumentException("resolution note is required");
        }
        this.status = DisputeStatus.RESOLVED;
        this.resolution = resolution;
        this.resolutionNote = note;
        this.decidedBy = decidedBy;
        this.decidedAt = decidedAt;
    }

    /** 迁移合法性由 DisputeStatus 唯一定义 */
    private void requireTransition(DisputeStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new OrderDisputeConflictException(
                    "dispute transition not allowed: " + status + " -> " + target);
        }
    }

    public Long getId() {
        return id;
    }

    public String getDisputeNo() {
        return disputeNo;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public Long getOpenedBy() {
        return openedBy;
    }

    public Long getSellerId() {
        return sellerId;
    }

    public String getListingNo() {
        return listingNo;
    }

    public String getUnitNo() {
        return unitNo;
    }

    public DisputeReason getReason() {
        return reason;
    }

    public String getDescription() {
        return description;
    }

    public String getEvidenceUrl() {
        return evidenceUrl;
    }

    public DisputeRequestType getRequestedResolution() {
        return requestedResolution;
    }

    public Long getRequestedAmount() {
        return requestedAmount;
    }

    public DisputeStatus getStatus() {
        return status;
    }

    public DisputeResolution getResolution() {
        return resolution;
    }

    public String getResolutionNote() {
        return resolutionNote;
    }

    public String getDecidedBy() {
        return decidedBy;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
