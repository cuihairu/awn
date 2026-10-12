package site.awn.audit.api;

/** 敏感操作审计记录命令 */
public record RecordAuditCommand(String actor, String action, String targetType,
                                 String targetId, String detailJson) {
}
