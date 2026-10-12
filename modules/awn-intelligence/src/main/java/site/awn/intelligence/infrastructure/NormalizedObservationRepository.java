package site.awn.intelligence.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import site.awn.intelligence.domain.NormalizedObservation;

public interface NormalizedObservationRepository extends JpaRepository<NormalizedObservation, Long> {
}
