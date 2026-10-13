package site.awn.payment.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import site.awn.payment.api.CreatePaymentCommand;
import site.awn.payment.api.GatewayChargeCommand;
import site.awn.payment.api.GatewayChargeResult;
import site.awn.payment.api.GatewayChannelStatus;
import site.awn.payment.api.GatewayRefundCommand;
import site.awn.payment.api.GatewayRefundResult;
import site.awn.payment.api.PaymentCallbackCommand;
import site.awn.payment.api.PaymentFacade;
import site.awn.payment.api.PaymentGateway;
import site.awn.payment.api.PaymentReconciliationReport;
import site.awn.payment.api.PaymentView;
import site.awn.payment.api.RefundCallbackCommand;
import site.awn.payment.api.RefundView;
import site.awn.payment.api.RequestRefundCommand;
import site.awn.payment.domain.Payment;
import site.awn.payment.domain.PaymentCallbackRejectedException;
import site.awn.payment.domain.PaymentConflictException;
import site.awn.payment.domain.PaymentNotFoundException;
import site.awn.payment.domain.PaymentStatus;
import site.awn.payment.domain.Refund;
import site.awn.payment.domain.RefundConflictException;
import site.awn.payment.domain.RefundNotFoundException;
import site.awn.payment.domain.RefundStatus;
import site.awn.payment.infrastructure.PaymentRepository;
import site.awn.payment.infrastructure.RefundRepository;
import site.awn.shared.alert.AlertCategory;
import site.awn.shared.alert.AlertRecorder;
import site.awn.shared.alert.AlertSeverity;
import site.awn.shared.outbox.OutboxMessage;
import site.awn.shared.outbox.OutboxMessageRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 支付用例：受理、回调（验签/去重/幂等状态更新）、退款（幂等/额度守卫）、对账。
 * 回调与订单超时释放竞态时支付单进入 UNKNOWN 待核实并写 Outbox 事件，
 * 不盲目确认（docs/architecture/payment-state-machine.md）。
 */
@Service
public class PaymentFacadeImpl implements PaymentFacade {

    private static final String AGGREGATE_TYPE = "PAYMENT";

    private final PaymentRepository payments;
    private final RefundRepository refunds;
    private final PaymentGatewayRouter router;
    private final OutboxMessageRepository outbox;
    private final site.awn.order.api.OrderFacade orders;
    private final AlertRecorder alerts;
    private final TransactionTemplate transactions;

    public PaymentFacadeImpl(PaymentRepository payments,
                             RefundRepository refunds,
                             PaymentGatewayRouter router,
                             OutboxMessageRepository outbox,
                             site.awn.order.api.OrderFacade orders,
                             AlertRecorder alerts,
                             TransactionTemplate transactions) {
        this.payments = payments;
        this.refunds = refunds;
        this.router = router;
        this.outbox = outbox;
        this.orders = orders;
        this.alerts = alerts;
        this.transactions = transactions;
    }

    @Override
    public PaymentView createPayment(CreatePaymentCommand command) {
        if (command.amount() < 0) {
            throw new IllegalArgumentException("payment amount must be non-negative");
        }
        // 1) 渠道校验先于落库：非法渠道不留孤儿支付单
        PaymentGateway gateway = router.forChannel(command.channel());

        // 2) 事务一：只落 CREATED 支付单（外部调用不出长事务）
        Payment payment = transactions.execute(status -> {
            Payment p = new Payment("PAY-" + UUID.randomUUID(), command.orderNo(),
                    command.channel(), command.currency(), command.amount());
            return payments.saveAndFlush(p);
        });

        // 3) 渠道受理在事务外：故障时支付单保留 CREATED，交对账闭环（G5）
        GatewayChargeResult accepted = gateway.createCharge(new GatewayChargeCommand(
                payment.getPaymentNo(), command.orderNo(), command.currency(), command.amount()));

        // 4) 事务二：回填渠道交易号 CREATED → PENDING
        Payment pending = transactions.execute(status -> {
            Payment fresh = payments.findByPaymentNo(payment.getPaymentNo())
                    .orElseThrow(() -> new PaymentNotFoundException(payment.getPaymentNo()));
            fresh.markPending(accepted.channelTxnNo());
            return fresh;
        });
        return toView(pending);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentView payment(String paymentNo) {
        return payments.findByPaymentNo(paymentNo).map(this::toView)
                .orElseThrow(() -> new PaymentNotFoundException(paymentNo));
    }

    @Override
    @Transactional
    public PaymentView handleCallback(PaymentCallbackCommand command) {
        // 1) 验签：失败即拒绝，不进入任何状态变更
        PaymentGateway gateway = router.forChannel(command.channel());
        if (!gateway.verifyCallback(command.rawBody(), command.headers())) {
            throw new PaymentCallbackRejectedException("callback signature verification failed");
        }

        // 2) 渠道交易号定位支付单（渠道交易号唯一，回调天然去重锚点）
        Payment payment = payments.findByChannelAndChannelTxnNo(command.channel(), command.channelTxnNo())
                .orElseThrow(() -> new PaymentNotFoundException(command.channelTxnNo()));

        // 3) 商户订单号（支付单绑定）、金额、币种、渠道一致性校验
        if (!payment.getCurrency().equals(command.currency())) {
            throw new PaymentConflictException("callback currency mismatch: " + command.currency());
        }
        if (payment.getAmount() != command.amount()) {
            throw new PaymentConflictException("callback amount mismatch: " + command.amount());
        }

        // 4) 渠道明确失败：仅 CREATED/PENDING 可直接失败；终态幂等返回
        if (!command.success()) {
            if (payments.markFailed(payment.getPaymentNo()) == 0) {
                return viewWithStatus(payment, payments.statusOf(payment.getPaymentNo())
                        .orElseThrow(() -> new PaymentNotFoundException(payment.getPaymentNo())));
            }
            return viewWithStatus(payment, PaymentStatus.FAILED);
        }

        // 5) 幂等：已成功支付单的重复回调直接返回原结果（只生效一次）
        PaymentStatus current = payments.statusOf(payment.getPaymentNo())
                .orElseThrow(() -> new PaymentNotFoundException(payment.getPaymentNo()));
        if (current == PaymentStatus.SUCCEEDED || current == PaymentStatus.REFUNDING
                || current == PaymentStatus.PARTIALLY_REFUNDED || current == PaymentStatus.REFUNDED) {
            return viewWithStatus(payment, current);
        }

        // 6) 与订单超时释放竞态：订单已终态（超时/取消）→ 支付结果待核实，
        //    未明确确认支付失败前不盲目确认订单，事件交由对账闭环处理
        site.awn.order.api.OrderView order = orders.orderOf(payment.getOrderNo());
        if (order.status() == site.awn.order.domain.OrderStatus.EXPIRED
                || order.status() == site.awn.order.domain.OrderStatus.CANCELLED) {
            if (payments.markUnknown(payment.getPaymentNo()) == 0) {
                current = payments.statusOf(payment.getPaymentNo())
                        .orElseThrow(() -> new PaymentNotFoundException(payment.getPaymentNo()));
                return viewWithStatus(payment, current);
            }
            outbox.save(OutboxMessage.pending(AGGREGATE_TYPE, payment.getPaymentNo(), "PAYMENT_UNKNOWN",
                    payload(payment, "UNKNOWN")));
            return viewWithStatus(payment, PaymentStatus.UNKNOWN);
        }

        // 7) 条件更新 CREATED/PENDING → SUCCEEDED：并发重复回调恰一个事务生效
        if (payments.markSucceeded(payment.getPaymentNo(), Instant.now()) == 0) {
            current = payments.statusOf(payment.getPaymentNo())
                    .orElseThrow(() -> new PaymentNotFoundException(payment.getPaymentNo()));
            if (current == PaymentStatus.SUCCEEDED) {
                return viewWithStatus(payment, current);
            }
            throw new PaymentConflictException("payment no longer accepts success callback: " + current);
        }
        // 条件更新未经过实体字段：同步内存态以返回准确的 paidAt
        payment.markSucceeded();

        // 8) 同事务写支付成功事件：Worker 分发 → 订单转 PAID（事件闭环）
        outbox.save(OutboxMessage.pending(AGGREGATE_TYPE, payment.getPaymentNo(), "PAYMENT_SUCCEEDED",
                payload(payment, "SUCCEEDED")));
        return viewWithStatus(payment, PaymentStatus.SUCCEEDED);
    }

    @Override
    public RefundView requestRefund(RequestRefundCommand command) {
        if (command.amount() <= 0) {
            throw new IllegalArgumentException("refund amount must be positive");
        }
        // 1) 幂等：同键同请求返回原退款单，不重复发起资金退款
        Refund existing = refunds.findByIdempotencyKey(command.idempotencyKey()).orElse(null);
        if (existing != null) {
            if (existing.getAmount() != command.amount()
                    || !existing.getPaymentNo().equals(command.paymentNo())) {
                throw new RefundConflictException(
                        "refund idempotency key already used by a different request: " + command.idempotencyKey());
            }
            return toView(existing);
        }

        // 2) 支付单必须已成功（或已在部分退款中）；加载在事务外：渠道受理需要其渠道与渠道交易号
        Payment payment = payments.findByPaymentNo(command.paymentNo())
                .orElseThrow(() -> new PaymentNotFoundException(command.paymentNo()));
        if (payment.getStatus() != PaymentStatus.SUCCEEDED
                && payment.getStatus() != PaymentStatus.PARTIALLY_REFUNDED) {
            throw new PaymentConflictException("refund not allowed from payment status " + payment.getStatus());
        }

        // 3) 事务一：累计额度守卫（占用中退款 + 本次 ≤ 支付金额）+ 落 CREATED 退款单
        Refund refund = transactions.execute(status -> {
            long occupied = refunds.sumOccupiedByPaymentNo(payment.getPaymentNo());
            if (occupied + command.amount() > payment.getAmount()) {
                throw new RefundConflictException("refund exceeds refundable amount: occupied=" + occupied
                        + ", requested=" + command.amount() + ", paid=" + payment.getAmount());
            }
            Refund r = new Refund("RFD-" + UUID.randomUUID(), payment.getPaymentNo(),
                    payment.getOrderNo(), command.idempotencyKey(), payment.getCurrency(),
                    command.amount(), command.reason());
            return refunds.saveAndFlush(r);
        });

        // 4) 渠道受理在事务外：故障时退款单保留 CREATED，交对账闭环（G5）
        GatewayRefundResult accepted = router.forChannel(payment.getChannel()).createRefund(
                new GatewayRefundCommand(refund.getRefundNo(), payment.getChannelTxnNo(),
                        refund.getCurrency(), refund.getAmount()));

        // 5) 事务二：回填渠道退款编号 CREATED → PENDING
        Refund pending = transactions.execute(status -> {
            Refund fresh = refunds.findByRefundNo(refund.getRefundNo())
                    .orElseThrow(() -> new RefundNotFoundException(refund.getRefundNo()));
            fresh.markPending(accepted.channelRefundNo());
            return fresh;
        });
        return toView(pending);
    }

    @Override
    @Transactional(readOnly = true)
    public RefundView refund(String refundNo) {
        return refunds.findByRefundNo(refundNo).map(this::toView)
                .orElseThrow(() -> new RefundNotFoundException(refundNo));
    }

    @Override
    @Transactional
    public RefundView handleRefundCallback(RefundCallbackCommand command) {
        PaymentGateway gateway = router.forChannel(command.channel());
        if (!gateway.verifyCallback(command.rawBody(), command.headers())) {
            throw new PaymentCallbackRejectedException("refund callback signature verification failed");
        }

        Refund refund = refunds.findByChannelRefundNo(command.channelRefundNo())
                .orElseThrow(() -> new RefundNotFoundException(command.channelRefundNo()));

        // 幂等：已成功的退款回调直接返回原结果
        if (refund.getStatus() == RefundStatus.SUCCEEDED) {
            return toView(refund);
        }
        if (!command.success()) {
            refund.markFailed();
            return toView(refund);
        }

        refund.markSucceeded();

        // 联动支付单退款状态：全额 → REFUNDED，部分 → PARTIALLY_REFUNDED
        Payment payment = payments.findByPaymentNo(refund.getPaymentNo())
                .orElseThrow(() -> new PaymentNotFoundException(refund.getPaymentNo()));
        long succeeded = refunds.sumSucceededByPaymentNo(refund.getPaymentNo());
        if (succeeded > payment.getAmount()) {
            throw new RefundConflictException("refunded total exceeds paid amount: " + succeeded);
        }
        if (succeeded == payment.getAmount()) {
            payment.applyRefundState(PaymentStatus.REFUNDED);
        } else {
            payment.applyRefundState(PaymentStatus.PARTIALLY_REFUNDED);
        }

        outbox.save(OutboxMessage.pending(AGGREGATE_TYPE, refund.getRefundNo(), "REFUND_SUCCEEDED",
                refundPayload(refund)));
        return toView(refund);
    }

    @Override
    @Transactional
    public PaymentReconciliationReport reconcile() {
        List<PaymentReconciliationReport.ReconciliationDifference> differences = new ArrayList<>();
        int paymentsChecked = 0;
        int refundsChecked = 0;

        // 未终态支付单 ↔ 渠道查询：发现差异只报告不修复
        for (Payment payment : payments.findByStatusIn(List.of(PaymentStatus.PENDING, PaymentStatus.UNKNOWN))) {
            paymentsChecked++;
            GatewayChannelStatus channelStatus = router.forChannel(payment.getChannel())
                    .queryCharge(payment.getChannelTxnNo()).status();
            if (differs(payment.getStatus().name(), channelStatus)) {
                differences.add(new PaymentReconciliationReport.ReconciliationDifference(
                        "PAYMENT", payment.getPaymentNo(), payment.getStatus().name(), channelStatus.name()));
            }
        }

        for (Refund refund : refunds.findByStatusIn(List.of(RefundStatus.PENDING, RefundStatus.UNKNOWN))) {
            refundsChecked++;
            GatewayChannelStatus channelStatus = router.forChannel(channelOf(refund))
                    .queryRefund(refund.getChannelRefundNo()).status();
            if (differs(refund.getStatus().name(), channelStatus)) {
                differences.add(new PaymentReconciliationReport.ReconciliationDifference(
                        "REFUND", refund.getRefundNo(), refund.getStatus().name(), channelStatus.name()));
            }
        }

        // 对账差异必须可告警：每条差异落一条 RECONCILIATION 告警
        for (PaymentReconciliationReport.ReconciliationDifference difference : differences) {
            alerts.raise(AlertCategory.RECONCILIATION, AlertSeverity.WARN,
                    "reconciliation difference: local=" + difference.localStatus()
                            + " channel=" + difference.channelStatus(),
                    difference.kind() + ":" + difference.reference());
        }

        return new PaymentReconciliationReport(paymentsChecked, refundsChecked, differences);
    }

    private String channelOf(Refund refund) {
        return payments.findByPaymentNo(refund.getPaymentNo())
                .map(Payment::getChannel)
                .orElseThrow(() -> new PaymentNotFoundException(refund.getPaymentNo()));
    }

    /** 渠道已给出确定结论而本地未跟上 → 差异 */
    private boolean differs(String localStatus, GatewayChannelStatus channelStatus) {
        return switch (channelStatus) {
            case SUCCEEDED -> !localStatus.equals("SUCCEEDED");
            case FAILED -> !localStatus.equals("FAILED");
            case PENDING -> !localStatus.equals("PENDING");
            case UNKNOWN -> false;
        };
    }

    private static String payload(Payment payment, String status) {
        return "{\"paymentNo\":\"" + payment.getPaymentNo()
                + "\",\"orderNo\":\"" + payment.getOrderNo()
                + "\",\"amount\":" + payment.getAmount()
                + ",\"currency\":\"" + payment.getCurrency()
                + "\",\"status\":\"" + status + "\"}";
    }

    private static String refundPayload(Refund refund) {
        return "{\"refundNo\":\"" + refund.getRefundNo()
                + "\",\"paymentNo\":\"" + refund.getPaymentNo()
                + "\",\"orderNo\":\"" + refund.getOrderNo()
                + "\",\"amount\":" + refund.getAmount() + "}";
    }

    private PaymentView toView(Payment p) {
        return new PaymentView(p.getPaymentNo(), p.getOrderNo(), p.getChannel(), p.getChannelTxnNo(),
                p.getStatus().name(), p.getCurrency(), p.getAmount(), p.getPaidAt());
    }

    private PaymentView viewWithStatus(Payment p, PaymentStatus status) {
        return new PaymentView(p.getPaymentNo(), p.getOrderNo(), p.getChannel(), p.getChannelTxnNo(),
                status.name(), p.getCurrency(), p.getAmount(), p.getPaidAt());
    }

    private RefundView toView(Refund r) {
        return new RefundView(r.getRefundNo(), r.getPaymentNo(), r.getOrderNo(), r.getChannelRefundNo(),
                r.getStatus().name(), r.getCurrency(), r.getAmount(), r.getReason());
    }
}
