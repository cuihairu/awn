package site.awn.shared.alert;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AlertRepository extends JpaRepository<AlertRecord, Long> {

    List<AlertRecord> findTop100ByAcknowledgedFalseOrderByCreatedAtDesc();

    List<AlertRecord> findTop100ByOrderByCreatedAtDesc();

    List<AlertRecord> findByCategoryAndAcknowledgedFalse(AlertCategory category);
}
