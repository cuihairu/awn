package site.awn.marketplace.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import site.awn.marketplace.domain.Listing;

import java.util.Optional;

public interface ListingRepository extends JpaRepository<Listing, Long> {

    Optional<Listing> findByListingNo(String listingNo);

    /** 预留守卫：LISTED → RESERVED。0 行 = 守卫失败（已预留/已成交/已下架）。 */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Listing l
               SET l.status = site.awn.marketplace.domain.ListingStatus.RESERVED
             WHERE l.id = :listingId
               AND l.status = site.awn.marketplace.domain.ListingStatus.LISTED
            """)
    int markReservedByListingId(@Param("listingId") Long listingId);

    /** 释放预留：RESERVED → LISTED。0 行 = 幂等重放或已成交/已下架。 */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Listing l
               SET l.status = site.awn.marketplace.domain.ListingStatus.LISTED
             WHERE l.id = :listingId
               AND l.status = site.awn.marketplace.domain.ListingStatus.RESERVED
            """)
    int markListedByListingId(@Param("listingId") Long listingId);

    /** 确认成交：RESERVED → SOLD。0 行 = 幂等重放或已释放/已下架。 */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Listing l
               SET l.status = site.awn.marketplace.domain.ListingStatus.SOLD
             WHERE l.id = :listingId
               AND l.status = site.awn.marketplace.domain.ListingStatus.RESERVED
            """)
    int markSoldByListingId(@Param("listingId") Long listingId);
}
