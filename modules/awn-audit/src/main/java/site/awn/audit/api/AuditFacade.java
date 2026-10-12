package site.awn.audit.api;

import java.util.List;

/** 审计对外接口：敏感操作留痕与查询（契约 GET /api/v1/admin/audit-logs） */
public interface AuditFacade {

    /** 记录一条审计；detailJson 可为 null */
    void record(RecordAuditCommand command);

    /** 最近审计（默认 100 条，新→旧） */
    List<AuditEntryView> recent();

    /** 按对象查询审计 */
    List<AuditEntryView> ofTarget(String targetType, String targetId);
}
