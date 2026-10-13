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

    /** 标量读取：绕过持久化上下文，条件更新后读取最新状态 */
    @Query("SELECT p.status FROM Payment p WHERE p.paymentNo = :paymentNo")
    Optional<site.awn.payment.domain.PaymentStatus> statusOf(@Param("paymentNo") String paymentNo);

    /** 条件更新：重复回调只生效一次（并发下恰一个事务更新到行）；UNKNOWN 查实后可迁入成功 */
    @Modifying
    @Query("""
            UPDATE Payment p SET p.status = site.awn.payment.domain.PaymentStatus.SUCCEEDED, p.paidAt = :paidAt
            WHERE p.paymentNo = :paymentNo AND p.status IN (site.awn.payment.domain.PaymentStatus.CREATED, site.awn.payment.domain.PaymentStatus.PENDING, site.awn.payment.domain.PaymentStatus.UNKNOWN)
            """)
    int markSucceeded(@Param("paymentNo") String paymentNo, @Param("paidAt") java.time.Instant paidAt);

    /** 条件更新：CREATED/PENDING 可直接失败，UNKNOWN 查实未扣款后迁入失败 */
    @Modifying
    @Query("""
            UPDATE Payment p SET p.status = site.awn.payment.domain.PaymentStatus.FAILED
            WHERE p.paymentNo = :paymentNo AND p.status IN (site.awn.payment.domain.PaymentStatus.CREATED, site.awn.payment.domain.PaymentStatus.PENDING, site.awn.payment.domain.PaymentStatus.UNKNOWN)
            """)
    int markFailed(@Param("paymentNo") String paymentNo);

    /** 条件更新：待核实（订单已超时释放等竞态） */
    @Modifying
    @Query("""
            UPDATE Payment p SET p.status = site.awn.payment.domain.PaymentStatus.UNKNOWN
            WHERE p.paymentNo = :paymentNo AND p.status IN (site.awn.payment.domain.PaymentStatus.CREATED, site.awn.payment.domain.PaymentStatus.PENDING)
            """)
    int markUnknown(@Param("paymentNo") String paymentNo);
}
