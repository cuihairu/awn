package site.awn.star.api;

/**
 * 给作品绑定人物身份（关系行带语义字段）。
 * roleType ∈ 白名单；roleName 自由文本（饰「李逍遥」、主题曲《稻香》），可为空；
 * billingOrder 可选署名次序。
 */
public record BindCastCommand(long personId, String roleType, String roleName, Integer billingOrder) {
}
