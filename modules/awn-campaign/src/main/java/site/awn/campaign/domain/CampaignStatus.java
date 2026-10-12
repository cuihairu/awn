package site.awn.campaign.domain;

/**
 * 专场状态机：DRAFT → VALIDATED → PUBLISHED → UNPUBLISHED → ARCHIVED
 */
public enum CampaignStatus {
    DRAFT,
    VALIDATED,
    PUBLISHED,
    UNPUBLISHED,
    ARCHIVED
}
