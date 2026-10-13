package site.awn.marketplace.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import site.awn.marketplace.domain.TicketUnit;

import java.util.Optional;

public interface TicketUnitRepository extends JpaRepository<TicketUnit, Long> {

    Optional<TicketUnit> findByUnitNo(String unitNo);

    Optional<TicketUnit> findByListingId(Long listingId);

    /**
     * 预留守卫（订单侧凭证型行）：仅当票品在库时才转为 RESERVED。
     * 0 行 = 守卫失败（已被抢、已成交或已作废）。
     * 并发抢单由这一条 UPDATE 的行锁 + 谓词裁决，赢者才能成交。
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE TicketUnit u
               SET u.status = site.awn.marketplace.domain.TicketUnitStatus.RESERVED
             WHERE u.listingId = :listingId
               AND u.status = site.awn.marketplace.domain.TicketUnitStatus.IN_STOCK
            """)
    int reserveByListingId(@Param("listingId") Long listingId);

    /** 释放预留：RESERVED → IN_STOCK。0 行 = 幂等重放或已成交/作废。 */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE TicketUnit u
               SET u.status = site.awn.marketplace.domain.TicketUnitStatus.IN_STOCK
             WHERE u.unitNo = :unitNo
               AND u.status = site.awn.marketplace.domain.TicketUnitStatus.RESERVED
            """)
    int releaseByUnitNo(@Param("unitNo") String unitNo);

    /** 确认成交：RESERVED → SOLD。0 行 = 幂等重放或已释放/作废。 */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE TicketUnit u
               SET u.status = site.awn.marketplace.domain.TicketUnitStatus.SOLD
             WHERE u.unitNo = :unitNo
               AND u.status = site.awn.marketplace.domain.TicketUnitStatus.RESERVED
            """)
    int confirmByUnitNo(@Param("unitNo") String unitNo);
}
