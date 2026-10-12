package site.awn.fulfillment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** 交付记录：卖家提交的凭证与平台核验结论（凭证 URL 仅白名单存储域） */
@Entity
@Table(name = "delivery_records")
public class DeliveryRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fulfillment_no", nullable = false)
    private String fulfillmentNo;

    @Column(name = "evidence_url", nullable = false)
    private String evidenceUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false)
    private DeliveryResult result;

    @Column(name = "note")
    private String note;

    @Column(name = "delivered_at", insertable = false, updatable = false)
    private Instant deliveredAt;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    protected DeliveryRecord() {
    }

    /** 卖家交付时创建，核验结论待定 */
    public DeliveryRecord(String fulfillmentNo, String evidenceUrl) {
        this.fulfillmentNo = fulfillmentNo;
        this.evidenceUrl = evidenceUrl;
        this.result = DeliveryResult.PENDING;
    }

    public void conclude(DeliveryResult result, String note) {
        this.result = result;
        this.note = note;
        this.verifiedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getFulfillmentNo() {
        return fulfillmentNo;
    }

    public String getEvidenceUrl() {
        return evidenceUrl;
    }

    public DeliveryResult getResult() {
        return result;
    }

    public String getNote() {
        return note;
    }

    public Instant getDeliveredAt() {
        return deliveredAt;
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
    }
}
