package site.awn.payment.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import site.awn.payment.domain.Payment;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByPaymentNo(String paymentNo);

    Optional<Payment> findByChannelAndChannelTxnNo(String channel, String channelTxnNo);

    List<Payment> findByStatusIn(List<site.awn.payment.domain.PaymentStatus> statuses);

    /** 订单维度支付历史（创建顺序） */
    List<Payment> findByOrderNoOrderByCreatedAtAsc(String orderNo);

    /** 支付创建串行化：同一订单只允许一个事务同时通过「可支付/无在途支付」守卫。 */
    @Query(value = "SELECT pg_advisory_xact_lock(hashtextextended(:orderNo, 0))", nativeQuery = true)
    Object lockOrderForPayment(@Param("orderNo") String orderNo);

    /** 退款额度守卫串行化：锁定支付行并与退款单创建保持在同一事务内。 */
    @Query(value = "SELECT id FROM payments WHERE payment_no = :paymentNo FOR UPDATE", nativeQuery = true)
    Optional<Long> lockPaymentForRefund(@Param("paymentNo") String paymentNo);

    /** 标量读取：绕过持久化上下文，条件更新后读取最新状态 */
    @Query("SELECT p.status FROM Payment p WHERE p.paymentNo = :paymentNo")
    Optional<site.awn.payment.domain.PaymentStatus> statusOf(@Param("paymentNo") String paymentNo);

    /**
     * 条件更新（原生 SQL，ADR-0007）：重复回调只生效一次（并发下恰一个事务更新到行）；
     * UNKNOWN 查实后可迁入成功。
     */
    @Modifying
    @Query(value = """
            UPDATE payments SET status = 'SUCCEEDED', paid_at = :paidAt
            WHERE payment_no = :paymentNo
              AND status IN ('CREATED', 'PENDING', 'UNKNOWN')
            """, nativeQuery = true)
    int markSucceeded(@Param("paymentNo") String paymentNo, @Param("paidAt") java.time.Instant paidAt);

    /** 条件更新（原生 SQL）：CREATED/PENDING 可直接失败，UNKNOWN 查实未扣款后迁入失败 */
    @Modifying
    @Query(value = """
            UPDATE payments SET status = 'FAILED'
            WHERE payment_no = :paymentNo
              AND status IN ('CREATED', 'PENDING', 'UNKNOWN')
            """, nativeQuery = true)
    int markFailed(@Param("paymentNo") String paymentNo);

    /** 条件更新（原生 SQL）：待核实（订单已超时释放等竞态） */
    @Modifying
    @Query(value = """
            UPDATE payments SET status = 'UNKNOWN'
            WHERE payment_no = :paymentNo
              AND status IN ('CREATED', 'PENDING')
            """, nativeQuery = true)
    int markUnknown(@Param("paymentNo") String paymentNo);

    /** 条件更新（原生 SQL）：退款成功联动，全额 → REFUNDED / 部分 → PARTIALLY_REFUNDED */
    @Modifying
    @Query(value = """
            UPDATE payments SET status = :status
            WHERE payment_no = :paymentNo
              AND status IN ('SUCCEEDED', 'PARTIALLY_REFUNDED')
            """, nativeQuery = true)
    int applyRefundState(@Param("paymentNo") String paymentNo, @Param("status") String status);
}
