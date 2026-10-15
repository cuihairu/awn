package site.awn.campaign.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 须知条目（购票须知、观演须知等）。
 *
 * <p>须知是运营逐条录入的独立内容，与区块配置快照（不可变 revision）分开存：
 * 区块 props 的 ruleDocumentId 按本表 id 引用，公开读按 displayOrder 排序返回纯文本。
 */
@Entity
@Table(name = "campaign_rule_documents")
public class CampaignRuleDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "campaign_id", nullable = false)
    private Long campaignId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String content;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected CampaignRuleDocument() {
    }

    public CampaignRuleDocument(Long campaignId, String title, String content, Integer displayOrder) {
        this.campaignId = campaignId;
        this.title = title;
        this.content = content;
        this.displayOrder = displayOrder;
    }

    public Long getId() {
        return id;
    }

    public Long getCampaignId() {
        return campaignId;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}