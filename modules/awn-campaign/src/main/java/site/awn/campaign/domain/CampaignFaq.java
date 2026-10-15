package site.awn.campaign.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 常见问题条目（问答对）。
 *
 * <p>问答内容是运营录入的展示型文本：区块 props 的 faqId 按本表 id 引用，
 * 公开读按 displayOrder 排序返回 question / answer，与渲染器读的展示形态同构。
 */
@Entity
@Table(name = "campaign_faqs")
public class CampaignFaq {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "campaign_id", nullable = false)
    private Long campaignId;

    @Column(nullable = false)
    private String question;

    @Column(nullable = false)
    private String answer;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected CampaignFaq() {
    }

    public CampaignFaq(Long campaignId, String question, String answer, Integer displayOrder) {
        this.campaignId = campaignId;
        this.question = question;
        this.answer = answer;
        this.displayOrder = displayOrder;
    }

    public Long getId() {
        return id;
    }

    public Long getCampaignId() {
        return campaignId;
    }

    public String getQuestion() {
        return question;
    }

    public String getAnswer() {
        return answer;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}