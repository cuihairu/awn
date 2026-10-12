package site.awn.marketplace.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import site.awn.marketplace.domain.TicketUnit;

import java.util.Optional;

public interface TicketUnitRepository extends JpaRepository<TicketUnit, Long> {

    Optional<TicketUnit> findByUnitNo(String unitNo);

    Optional<TicketUnit> findByListingId(Long listingId);
}
