package site.awn.intelligence.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import site.awn.intelligence.domain.DataSource;

import java.util.Optional;

public interface DataSourceRepository extends JpaRepository<DataSource, Long> {

    Optional<DataSource> findBySourceNo(String sourceNo);
}
