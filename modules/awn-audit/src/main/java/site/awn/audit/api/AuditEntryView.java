package site.awn.audit.api;

import java.time.Instant;

/** 审计日志视图 */
public record AuditEntryView(Long id, String actor, String action, String targetType,
                             String targetId, String detail, Instant createdAt) {
}
