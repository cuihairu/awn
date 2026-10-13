package site.awn.payment.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import site.awn.payment.domain.Refund;
import site.awn.payment.domain.RefundStatus;

import java.util.List;
import java.util.Optional;

public interface RefundRepository extends JpaRepository<Refund, Long> {

    Optional<Refund> findByRefundNo(String refundNo);

    Optional<Refund> findByIdempotencyKey(String idempotencyKey);

    Optional<Refund> findByChannelRefundNo(String channelRefundNo);

    List<Refund> findByStatusIn(List<RefundStatus> statuses);

    List<Refund> findByPaymentNoOrderByCreatedAtAsc(String paymentNo);

    /** 标量读取：绕过持久化上下文，条件更新后读取最新状态 */
    @Query(value = "SELECT status FROM refunds WHERE refund_no = :refundNo", nativeQuery = true)
    Optional<String> statusOf(@Param("refundNo") String refundNo);

    /** 渠道受理（原生 SQL，ADR-0007）：仅 CREATED 可绑定渠道退款编号并转在途 */
    @Modifying
    @Query(value = """
            UPDATE refunds SET status = 'PENDING', channel_refund_no = :channelRefundNo
            WHERE refund_no = :refundNo AND status = 'CREATED'
            """, nativeQuery = true)
    int markPending(@Param("refundNo") String refundNo, @Param("channelRefundNo") String channelRefundNo);

    /** 条件更新（原生 SQL）：重复退款回调只生效一次；UNKNOWN 查实后可迁入成功 */
    @Modifying
    @Query(value = """
            UPDATE refunds SET status = 'SUCCEEDED'
            WHERE refund_no = :refundNo
              AND status IN ('CREATED', 'PENDING', 'UNKNOWN')
            """, nativeQuery = true)
    int markSucceeded(@Param("refundNo") String refundNo);

    /** 条件更新（原生 SQL）：渠道明确失败，终态幂等 */
    @Modifying
    @Query(value = """
            UPDATE refunds SET status = 'FAILED'
            WHERE refund_no = :refundNo
              AND status IN ('CREATED', 'PENDING', 'UNKNOWN')
            """, nativeQuery = true)
    int markFailed(@Param("refundNo") String refundNo);

    /**
     * 占用中的退款额度：CREATED/PENDING/SUCCEEDED/UNKNOWN 都计入，
     * 保证并发重复退款与渠道结果未知时不超退。
     */
    @Query("""
            SELECT COALESCE(SUM(r.amount), 0) FROM Refund r
            WHERE r.paymentNo = :paymentNo
              AND r.status IN (site.awn.payment.domain.RefundStatus.CREATED,
                               site.awn.payment.domain.RefundStatus.PENDING,
                               site.awn.payment.domain.RefundStatus.SUCCEEDED,
                               site.awn.payment.domain.RefundStatus.UNKNOWN)
            """)
    long sumOccupiedByPaymentNo(@Param("paymentNo") String paymentNo);

    /** 已成功退款累计（用于支付单退款状态联动与全额判定） */
    @Query("""
            SELECT COALESCE(SUM(r.amount), 0) FROM Refund r
            WHERE r.paymentNo = :paymentNo AND r.status = site.awn.payment.domain.RefundStatus.SUCCEEDED
            """)
    long sumSucceededByPaymentNo(@Param("paymentNo") String paymentNo);

    long countByPaymentNo(String paymentNo);
}
