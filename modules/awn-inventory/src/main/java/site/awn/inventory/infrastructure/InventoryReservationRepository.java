package site.awn.inventory.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import site.awn.inventory.domain.InventoryReservation;
import site.awn.inventory.domain.ReservationStatus;

import java.util.Optional;

public interface InventoryReservationRepository extends JpaRepository<InventoryReservation, Long> {

    Optional<InventoryReservation> findByReservationNo(String reservationNo);

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
}
