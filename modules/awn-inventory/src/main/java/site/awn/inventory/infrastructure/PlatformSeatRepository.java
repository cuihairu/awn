package site.awn.inventory.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import site.awn.inventory.domain.PlatformSeat;

import java.util.List;
import java.util.Optional;

/**
 * 平台座位仓储：逐座状态迁移的唯一写入路径是守卫条件 UPDATE（并发抢座的裁决点）。
 * 实体层 transitionTo 只校验状态机合法性；仓储层 WHERE status = :expected 兜底并发，
 * 返回 0 行表示座位已被其他事务抢先迁移。updated_at 由触发器维护。
 */
public interface PlatformSeatRepository extends JpaRepository<PlatformSeat, Long> {

    Optional<PlatformSeat> findBySeatNo(String seatNo);

    List<PlatformSeat> findByProductCodeOrderById(String productCode);

    List<PlatformSeat> findBySessionCodeOrderById(String sessionCode);

    boolean existsBySessionCodeAndSectionNameAndRowLabelAndSeatLabel(
            String sessionCode, String sectionName, String rowLabel, String seatLabel);

    /** 预留：IN_STOCK → RESERVED 并登记订单号；0 行表示座位已被抢走或状态已变 */
    @Modifying
    @Query(value = """
            UPDATE platform_seats
               SET status = 'RESERVED', order_no = :orderNo
             WHERE seat_no = :seatNo
               AND status = 'IN_STOCK'
            """, nativeQuery = true)
    int reserve(@Param("seatNo") String seatNo, @Param("orderNo") String orderNo);

    /** 释放：RESERVED → IN_STOCK 并清空订单号；0 行表示已不在预留态 */
    @Modifying
    @Query(value = """
            UPDATE platform_seats
               SET status = 'IN_STOCK', order_no = NULL
             WHERE seat_no = :seatNo
               AND status = 'RESERVED'
            """, nativeQuery = true)
    int release(@Param("seatNo") String seatNo);

    /** 确认：RESERVED → SOLD；0 行表示已不在预留态 */
    @Modifying
    @Query(value = """
            UPDATE platform_seats
               SET status = 'SOLD'
             WHERE seat_no = :seatNo
               AND status = 'RESERVED'
            """, nativeQuery = true)
    int confirm(@Param("seatNo") String seatNo);

    /** 交付：SOLD → DELIVERED；0 行表示未在售 */
    @Modifying
    @Query(value = """
            UPDATE platform_seats
               SET status = 'DELIVERED'
             WHERE seat_no = :seatNo
               AND status = 'SOLD'
            """, nativeQuery = true)
    int deliver(@Param("seatNo") String seatNo);

    /** 作废：IN_STOCK / RESERVED → INVALID；0 行表示已售/已交付/已作废 */
    @Modifying
    @Query(value = """
            UPDATE platform_seats
               SET status = 'INVALID', order_no = NULL
             WHERE seat_no = :seatNo
               AND status IN ('IN_STOCK', 'RESERVED')
            """, nativeQuery = true)
    int invalidate(@Param("seatNo") String seatNo);
}
