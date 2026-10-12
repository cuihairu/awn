package site.awn.payment.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
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
