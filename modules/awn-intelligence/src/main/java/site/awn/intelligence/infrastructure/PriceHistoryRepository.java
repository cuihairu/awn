package site.awn.intelligence.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import site.awn.intelligence.domain.PriceHistoryEntry;

import java.util.Collection;
import java.util.List;

public interface PriceHistoryRepository extends JpaRepository<PriceHistoryEntry, Long> {

    List<PriceHistoryEntry> findByProductCodeOrderByObservedAtDesc(String productCode);

    /** 按场次代码集合查价格历史（活动维度价格走势），按观测时间倒序 */
    List<PriceHistoryEntry> findBySessionCodeInOrderByObservedAtDesc(Collection<String> sessionCodes);
}
