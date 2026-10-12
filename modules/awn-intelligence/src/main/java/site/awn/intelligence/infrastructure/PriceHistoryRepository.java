package site.awn.intelligence.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import site.awn.intelligence.domain.PriceHistoryEntry;

import java.util.List;

public interface PriceHistoryRepository extends JpaRepository<PriceHistoryEntry, Long> {

    List<PriceHistoryEntry> findByProductCodeOrderByObservedAtDesc(String productCode);
}
