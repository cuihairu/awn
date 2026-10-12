package site.awn.event.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import site.awn.event.domain.Venue;

import java.util.Optional;

public interface VenueRepository extends JpaRepository<Venue, Long> {

    Optional<Venue> findByVenueCode(String venueCode);
}
