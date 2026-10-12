package site.awn.catalog.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import site.awn.catalog.domain.TicketCategory;

import java.util.Optional;

public interface TicketCategoryRepository extends JpaRepository<TicketCategory, Long> {

    Optional<TicketCategory> findByCategoryCode(String categoryCode);
}
