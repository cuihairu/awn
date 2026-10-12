package site.awn.intelligence.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import site.awn.intelligence.domain.RawObservation;

import java.util.List;

public interface RawObservationRepository extends JpaRepository<RawObservation, Long> {

    List<RawObservation> findByQualityStatus(site.awn.intelligence.domain.QualityStatus status);

    boolean existsBySourceIdAndSourceEventRef(Long sourceId, String sourceEventRef);
}
