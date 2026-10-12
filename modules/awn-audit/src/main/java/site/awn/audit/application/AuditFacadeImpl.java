package site.awn.audit.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.awn.audit.api.AuditEntryView;
import site.awn.audit.api.AuditFacade;
import site.awn.audit.api.RecordAuditCommand;
import site.awn.audit.domain.AuditLog;
import site.awn.audit.infrastructure.AuditLogRepository;

import java.util.List;

/** 审计用例实现：记录与查询。 */
@Service
public class AuditFacadeImpl implements AuditFacade {

    private final AuditLogRepository logs;

    public AuditFacadeImpl(AuditLogRepository logs) {
        this.logs = logs;
    }

    @Override
    @Transactional
    public void record(RecordAuditCommand command) {
        if (command.actor() == null || command.actor().isBlank()) {
            throw new IllegalArgumentException("audit actor is required");
        }
        if (command.action() == null || command.action().isBlank()) {
            throw new IllegalArgumentException("audit action is required");
        }
        logs.save(new AuditLog(command.actor(), command.action(), command.targetType(),
                command.targetId(), command.detailJson()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AuditEntryView> recent() {
        return logs.findTop100ByOrderByIdDesc().stream().map(this::toView).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AuditEntryView> ofTarget(String targetType, String targetId) {
        return logs.findTop100ByTargetTypeAndTargetIdOrderByIdDesc(targetType, targetId)
                .stream().map(this::toView).toList();
    }

    private AuditEntryView toView(AuditLog log) {
        return new AuditEntryView(log.getId(), log.getActor(), log.getAction(),
                log.getTargetType(), log.getTargetId(), log.getDetail(), log.getCreatedAt());
    }
}
