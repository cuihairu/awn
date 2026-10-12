package site.awn.marketplace.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import site.awn.marketplace.domain.Listing;

import java.util.Optional;

public interface ListingRepository extends JpaRepository<Listing, Long> {

    Optional<Listing> findByListingNo(String listingNo);
}
