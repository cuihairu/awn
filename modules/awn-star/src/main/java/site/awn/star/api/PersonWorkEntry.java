package site.awn.star.api;

/**
 * 人物详情里的作品行 / 作品详情里的人员行的共用形状：
 * 关系行 + 对端摘要。roleType 为结构化身份，roleName 为自由文本（如「饰 李逍遥」）。
 */
public record PersonWorkEntry(
        Long personId,
        Long workId,
        String personName,
        String workTitle,
        String workType,
        Integer workYear,
        String roleType,
        String roleName,
        Integer billingOrder) {
}
