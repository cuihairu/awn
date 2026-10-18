package site.awn.payment.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import site.awn.payment.api.PaymentUnknownResolutionResult;
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
import site.awn.shared.outbox.OutboxMessageRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 支付用例：受理、回调（验签/去重/幂等状态更新）、退款（幂等/额度守卫）、对账。
 * 回调与订单超时释放竞态时支付单进入 UNKNOWN 待核实并写 Outbox 事件，
 * 不盲目确认（docs/architecture/payment-state-machine.md）。
 */
@Service
public class PaymentFacadeImpl implements PaymentFacade {

    private static final Logger log = LoggerFactory.getLogger(PaymentFacadeImpl.class);

    private static final String AGGREGATE_TYPE = "PAYMENT";

    /** 在途或已成功的支付单状态：同订单存在即阻止新发起支付单（防重复扣款） */
    private static final Set<PaymentStatus> BLOCKS_NEW_PAYMENT = EnumSet.of(
            PaymentStatus.CREATED, PaymentStatus.PENDING, PaymentStatus.UNKNOWN,
            PaymentStatus.SUCCEEDED, PaymentStatus.REFUNDING, PaymentStatus.PARTIALLY_REFUNDED,
            PaymentStatus.REFUNDED);

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

        // 2) 事务一：按订单串行化并在落 CREATED 前检查订单与现存支付单。
        //    同订单已有在途/成功支付时拒绝；FAILED 历史保留，允许显式重新发起。
        Payment payment = transactions.execute(status -> {
            payments.lockOrderForPayment(command.orderNo());
            site.awn.order.api.OrderView order = orders.orderOf(command.orderNo());
            if (order.status() != site.awn.order.domain.OrderStatus.PENDING_PAYMENT
                    || (order.expiresAt() != null && !order.expiresAt().isAfter(Instant.now()))) {
                throw new PaymentConflictException("order is no longer payable: " + order.status());
            }
            boolean alreadyActive = payments.findByOrderNoOrderByCreatedAtAsc(command.orderNo()).stream()
                    .anyMatch(existing -> BLOCKS_NEW_PAYMENT.contains(existing.getStatus()));
            if (alreadyActive) {
                throw new PaymentConflictException("order already has an active or successful payment");
            }
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
    public List<String> channels() {
        return router.channels();
    }

    /**
     * 失败单重试（契约「支付失败重试与换渠道」）：守卫通过后与首笔支付同一创建路径，
     * 原失败单保持 FAILED、历史不改写；新单金额/币种沿用源单（源自订单应付快照）。
     */
    @Override
    public PaymentView retryPayment(String paymentNo, String requestedChannel) {
        Payment source = payments.findByPaymentNo(paymentNo)
                .orElseThrow(() -> new PaymentNotFoundException(paymentNo));
        if (source.getStatus() != PaymentStatus.FAILED) {
            throw new PaymentConflictException(
                    "retry only allowed from failed payment: " + source.getStatus());
        }
        site.awn.order.api.OrderView order = orders.orderOf(source.getOrderNo());
        if (order.status() != site.awn.order.domain.OrderStatus.PENDING_PAYMENT
                || (order.expiresAt() != null && !order.expiresAt().isAfter(Instant.now()))) {
            throw new site.awn.order.domain.OrderConflictException(
                    "order no longer payable: " + order.status());
        }
        // 同订单已有在途/成功支付单：不允许再发起新单，防重复扣款
        if (payments.findByOrderNoOrderByCreatedAtAsc(source.getOrderNo()).stream()
                .anyMatch(other -> !other.getPaymentNo().equals(source.getPaymentNo())
                        && BLOCKS_NEW_PAYMENT.contains(other.getStatus()))) {
            throw new PaymentConflictException(
                    "another payment is in flight or already succeeded for order: "
                            + source.getOrderNo());
        }
        String channel = requestedChannel == null || requestedChannel.isBlank()
                ? source.getChannel() : requestedChannel;
        return createPayment(new CreatePaymentCommand(source.getOrderNo(), channel,
                source.getCurrency(), source.getAmount()));
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentView payment(String paymentNo) {
        return payments.findByPaymentNo(paymentNo).map(this::toView)
                .orElseThrow(() -> new PaymentNotFoundException(paymentNo));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentView> paymentsOfOrder(String orderNo) {
        return payments.findByOrderNoOrderByCreatedAtAsc(orderNo).stream()
                .map(this::toView)
                .toList();
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
            outbox.insertPending(AGGREGATE_TYPE, payment.getPaymentNo(), "PAYMENT_UNKNOWN",
                    payload(payment, "UNKNOWN"));
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
        outbox.insertPending(AGGREGATE_TYPE, payment.getPaymentNo(), "PAYMENT_SUCCEEDED",
                payload(payment, "SUCCEEDED"));
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
            payments.lockPaymentForRefund(payment.getPaymentNo())
                    .orElseThrow(() -> new PaymentNotFoundException(payment.getPaymentNo()));
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

        // 5) 事务二：回填渠道退款编号 CREATED → PENDING（原生守卫更新，并发只一笔生效）
        transactions.executeWithoutResult(status -> {
            int moved = refunds.markPending(refund.getRefundNo(), accepted.channelRefundNo());
            if (moved == 0) {
                throw new RefundConflictException("refund no longer accepts channel acceptance: "
                        + currentStatus(refund));
            }
        });
        return view(refund, accepted.channelRefundNo(), RefundStatus.PENDING);
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
            // 原生守卫更新：重复失败回调只生效一次，输家按现状返回
            if (refunds.markFailed(refund.getRefundNo()) == 0) {
                return view(refund, refund.getChannelRefundNo(), currentStatus(refund));
            }
            return view(refund, refund.getChannelRefundNo(), RefundStatus.FAILED);
        }

        // 原生守卫更新：重复退款回调只生效一次；输家不重复写事件、不重复联动支付单
        if (refunds.markSucceeded(refund.getRefundNo()) == 0) {
            return view(refund, refund.getChannelRefundNo(), currentStatus(refund));
        }

        // 联动支付单退款状态（原生守卫更新）：全额 → REFUNDED，部分 → PARTIALLY_REFUNDED
        Payment payment = payments.findByPaymentNo(refund.getPaymentNo())
                .orElseThrow(() -> new PaymentNotFoundException(refund.getPaymentNo()));
        long succeeded = refunds.sumSucceededByPaymentNo(refund.getPaymentNo());
        if (succeeded > payment.getAmount()) {
            throw new RefundConflictException("refunded total exceeds paid amount: " + succeeded);
        }
        PaymentStatus target = succeeded == payment.getAmount()
                ? PaymentStatus.REFUNDED : PaymentStatus.PARTIALLY_REFUNDED;
        if (payments.applyRefundState(payment.getPaymentNo(), target.name()) == 0) {
            log.warn("payment refund state transition lost race: {}", payment.getPaymentNo());
        }

        outbox.insertPending(AGGREGATE_TYPE, refund.getRefundNo(), "REFUND_SUCCEEDED",
                refundPayload(refund));
        return view(refund, refund.getChannelRefundNo(), RefundStatus.SUCCEEDED);
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
            GatewayChannelStatus channelStatus;
            try {
                channelStatus = router.forChannel(payment.getChannel())
                        .queryCharge(payment.getChannelTxnNo()).status();
            } catch (PaymentConflictException e) {
                // 渠道未注册（下线/配置缺失）：「无法核对」本身就是差异，
                // 报告成 UNSUPPORTED 而不是让整个对账中断（对账是只读报告）
                differences.add(new PaymentReconciliationReport.ReconciliationDifference(
                        "PAYMENT", payment.getPaymentNo(), payment.getStatus().name(), "UNSUPPORTED"));
                continue;
            }
            if (differs(payment.getStatus().name(), channelStatus)) {
                differences.add(new PaymentReconciliationReport.ReconciliationDifference(
                        "PAYMENT", payment.getPaymentNo(), payment.getStatus().name(), channelStatus.name()));
            }
        }

        for (Refund refund : refunds.findByStatusIn(List.of(RefundStatus.PENDING, RefundStatus.UNKNOWN))) {
            refundsChecked++;
            GatewayChannelStatus channelStatus;
            try {
                channelStatus = router.forChannel(channelOf(refund))
                        .queryRefund(refund.getChannelRefundNo()).status();
            } catch (PaymentConflictException e) {
                differences.add(new PaymentReconciliationReport.ReconciliationDifference(
                        "REFUND", refund.getRefundNo(), refund.getStatus().name(), "UNSUPPORTED"));
                continue;
            }
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

    /**
     * UNKNOWN 支付单核实（G6）：渠道查询在事务外（外部调用不出长事务），
     * 查实后按守卫条件更新迁移；查询故障保持 UNKNOWN 下轮再核实。
     * 查实成功但订单已终态 → 异常单处理（原路退款）。
     */
    @Override
    public PaymentUnknownResolutionResult resolveUnknownPayments() {
        int resolvedSucceeded = 0;
        int resolvedFailed = 0;
        int stillUnknown = 0;
        for (Payment payment : payments.findByStatusIn(List.of(PaymentStatus.UNKNOWN))) {
            GatewayChannelStatus channelStatus;
            try {
                channelStatus = router.forChannel(payment.getChannel())
                        .queryCharge(payment.getChannelTxnNo()).status();
            } catch (RuntimeException e) {
                log.warn("payment unknown resolution channel query failed: {}", payment.getPaymentNo(), e);
                stillUnknown++;
                continue;
            }
            switch (channelStatus) {
                case SUCCEEDED -> {
                    Boolean resolved = transactions.execute(tx ->
                            payments.markSucceeded(payment.getPaymentNo(), Instant.now()) == 1);
                    if (Boolean.TRUE.equals(resolved)) {
                        resolvedSucceeded++;
                        handleOrderReleasedAfterSuccess(payment);
                    }
                }
                case FAILED -> {
                    Boolean resolved = transactions.execute(tx ->
                            payments.markFailed(payment.getPaymentNo()) == 1);
                    if (Boolean.TRUE.equals(resolved)) {
                        resolvedFailed++;
                    }
                }
                default -> stillUnknown++;
            }
        }
        return new PaymentUnknownResolutionResult(resolvedSucceeded, resolvedFailed, stillUnknown);
    }

    /**
     * 异常单处理：支付查实成功但订单已终态（库存已释放）→
     * 告警 + 原路全额退款；不假装履约，不重复扣减。
     * 幂等键取支付单派生值：同一支付单的异常退款只发起一次。
     */
    private void handleOrderReleasedAfterSuccess(Payment payment) {
        site.awn.order.api.OrderView order = orders.orderOf(payment.getOrderNo());
        if (order.status() != site.awn.order.domain.OrderStatus.EXPIRED
                && order.status() != site.awn.order.domain.OrderStatus.CANCELLED) {
            return;
        }
        alerts.raise(AlertCategory.PAYMENT, AlertSeverity.CRITICAL,
                "payment confirmed after order released, auto refund initiated: " + payment.getPaymentNo(),
                payment.getPaymentNo());
        requestRefund(new RequestRefundCommand(payment.getPaymentNo(),
                "after-release-" + payment.getPaymentNo(), payment.getAmount(),
                "payment confirmed after order expired"));
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
        return view(p, p.getStatus());
    }

    private PaymentView viewWithStatus(Payment p, PaymentStatus status) {
        return view(p, status);
    }

    private PaymentView view(Payment p, PaymentStatus status) {
        return new PaymentView(p.getPaymentNo(), p.getOrderNo(), p.getChannel(), p.getChannelTxnNo(),
                status.name(), p.getCurrency(), p.getAmount(), p.getPaidAt(),
                retryable(p, status));
    }

    /** 可重试判定（契约「支付失败重试与换渠道」）：失败态 + 订单可支付 + 同订单无在途/成功支付单 */
    private boolean retryable(Payment p, PaymentStatus status) {
        if (status != PaymentStatus.FAILED) {
            return false;
        }
        site.awn.order.api.OrderView order = orders.orderOf(p.getOrderNo());
        if (order.status() != site.awn.order.domain.OrderStatus.PENDING_PAYMENT
                || (order.expiresAt() != null && !order.expiresAt().isAfter(Instant.now()))) {
            return false;
        }
        return payments.findByOrderNoOrderByCreatedAtAsc(p.getOrderNo()).stream()
                .noneMatch(other -> !other.getPaymentNo().equals(p.getPaymentNo())
                        && BLOCKS_NEW_PAYMENT.contains(other.getStatus()));
    }

    private RefundView toView(Refund r) {
        return view(r, r.getChannelRefundNo(), r.getStatus());
    }

    /** 原生守卫更新不经过实体字段：手工构造视图以返回准确状态 */
    private RefundView view(Refund r, String channelRefundNo, RefundStatus status) {
        return new RefundView(r.getRefundNo(), r.getPaymentNo(), r.getOrderNo(), channelRefundNo,
                status.name(), r.getCurrency(), r.getAmount(), r.getReason());
    }

    private RefundStatus currentStatus(Refund refund) {
        return refunds.statusOf(refund.getRefundNo())
                .map(RefundStatus::valueOf)
                .orElseThrow(() -> new RefundConflictException(
                        "refund status unavailable: " + refund.getRefundNo()));
    }
}
