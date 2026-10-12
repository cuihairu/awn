package site.awn.audit.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import site.awn.audit.domain.AuditLog;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findTop100ByOrderByIdDesc();

    List<AuditLog> findTop100ByTargetTypeAndTargetIdOrderByIdDesc(String targetType, String targetId);
}
