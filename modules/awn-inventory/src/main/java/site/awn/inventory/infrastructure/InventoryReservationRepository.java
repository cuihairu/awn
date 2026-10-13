package site.awn.inventory.infrastructure;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import site.awn.inventory.domain.InventoryReservation;
import site.awn.inventory.domain.ReservationStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface InventoryReservationRepository extends JpaRepository<InventoryReservation, Long> {

    Optional<InventoryReservation> findByReservationNo(String reservationNo);

    /** 到期扫描：已过期（expiresAt < now）且仍为 ACTIVE 的预留，分批取 */
    @Query("""
            SELECT r.reservationNo
              FROM InventoryReservation r
             WHERE r.status = :status
               AND r.expiresAt IS NOT NULL
               AND r.expiresAt < :now
            """)
    List<String> findExpiredActive(@Param("status") ReservationStatus status,
                                   @Param("now") Instant now,
                                   Pageable pageable);

    /**
     * 带状态守卫的迁移：仅当当前状态为 from 时改为 to。
     * 确认与释放的竞态由这一条 UPDATE 的行锁 + 谓词裁决，赢者才能改余额。
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE InventoryReservation r
               SET r.status = :to
             WHERE r.reservationNo = :reservationNo
               AND r.status = :from
            """)
    int transition(@Param("reservationNo") String reservationNo,
                   @Param("from") ReservationStatus from,
                   @Param("to") ReservationStatus to);

    /** 对账用：按池汇总某状态的预留数量（ACTIVE→期望 reserved，CONFIRMED→期望 sold） */
    @Query("""
            SELECT r.inventoryPoolId, SUM(r.quantity)
              FROM InventoryReservation r
             WHERE r.status = :status
             GROUP BY r.inventoryPoolId
            """)
    java.util.List<Object[]> sumQuantitiesByStatus(@Param("status") ReservationStatus status);
}
