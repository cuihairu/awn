package site.awn.shared.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import site.awn.shared.alert.AlertCategory;
import site.awn.shared.alert.AlertRecorder;
import site.awn.shared.alert.AlertSeverity;
import site.awn.shared.inbox.InboxMessage;
import site.awn.shared.inbox.InboxMessageRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Outbox 分发：进程重启不丢事件（PENDING 持久化），同一事件不重复投递
 * （Inbox 消费者维度去重）。单条消息独立事务：处理失败按退避重试，
 * 超上限转 FAILED 等待告警，不阻塞其余消息。
 */
@Service
public class OutboxDispatchService {

    private static final Logger log = LoggerFactory.getLogger(OutboxDispatchService.class);

    /** 最大重试次数，超过转 FAILED */
    static final int MAX_ATTEMPTS = 5;
    /** 退避基数：第 n 次失败后 n * 30s 再试 */
    static final Duration BACKOFF_BASE = Duration.ofSeconds(30);
    /** PROCESSING 租约：超时未完成视为崩溃残留，允许重新认领 */
    static final Duration LEASE_TIMEOUT = Duration.ofMinutes(5);

    private final OutboxMessageRepository outbox;
    private final InboxMessageRepository inbox;
    private final List<OutboxEventHandler> handlers;
    private final TransactionTemplate transactions;
    private final AlertRecorder alerts;

    public OutboxDispatchService(OutboxMessageRepository outbox,
                                 InboxMessageRepository inbox,
                                 List<OutboxEventHandler> handlers,
                                 TransactionTemplate transactions,
                                 AlertRecorder alerts) {
        this.outbox = outbox;
        this.inbox = inbox;
        this.handlers = List.copyOf(handlers);
        this.transactions = transactions;
        this.alerts = alerts;
    }

    /**
     * 分发一轮到期消息。
     *
     * @return 本轮认领的消息数
     */
    public int dispatchDue(Instant now) {
        List<Long> claimed = transactions.execute(tx -> claimDue(now));
        if (claimed == null || claimed.isEmpty()) {
            return 0;
        }
        int delivered = 0;
        for (Long id : claimed) {
            try {
                transactions.executeWithoutResult(tx -> deliverOne(id, now));
                delivered++;
            } catch (Exception e) {
                registerFailure(id, now, e);
            }
        }
        return delivered;
    }

    /** 认领：到期 PENDING 与租约过期的 PROCESSING；FOR UPDATE SKIP LOCKED 支持多实例安全 */
    private List<Long> claimDue(Instant now) {
        Instant leaseDeadline = now.minus(LEASE_TIMEOUT);
        List<Long> ids = outbox.lockDueIds(now, leaseDeadline, 50);
        if (!ids.isEmpty()) {
            outbox.claim(ids);
        }
        return ids;
    }

    /** 单条消息投递：消费者副作用 + Inbox 去重记录 + PROCESSED 同事务提交 */
    private void deliverOne(Long id, Instant now) {
        OutboxMessage message = outbox.findById(id).orElseThrow();
        boolean anyHandler = false;
        for (OutboxEventHandler handler : handlers) {
            if (!handler.supports(message.getEventType())) {
                continue;
            }
            anyHandler = true;
            // Inbox 去重：已消费直接跳过（重放安全）
            if (inbox.existsByConsumerAndMessageId(handler.consumer(), id)) {
                continue;
            }
            handler.handle(message);
            inbox.save(new InboxMessage(handler.consumer(), id, message.getEventType()));
        }
        message.markProcessed(now);
        if (!anyHandler) {
            log.debug("outbox message {} type {} has no consumer; marked processed", id, message.getEventType());
        }
    }

    /** 失败登记：独立事务，按退避重试；超上限转 FAILED 并触发支付/消费异常告警 */
    private void registerFailure(Long id, Instant now, Exception cause) {
        log.warn("outbox message {} dispatch failed: {}", id, cause.getMessage());
        transactions.executeWithoutResult(tx -> {
            OutboxMessage message = outbox.findById(id).orElse(null);
            if (message == null) {
                return;
            }
            int attempts = message.getAttempts() + 1;
            if (attempts >= MAX_ATTEMPTS) {
                message.fail(attempts);
                log.error("outbox message {} moved to FAILED after {} attempts", id, attempts);
                // 事件不可消费属于异常流程入口：支付/退款事件 → 支付异常告警；其余 → 对账告警
                AlertCategory category = message.getEventType().startsWith("PAYMENT")
                        || message.getEventType().startsWith("REFUND")
                        ? AlertCategory.PAYMENT : AlertCategory.RECONCILIATION;
                alerts.raise(category, AlertSeverity.CRITICAL,
                        "outbox message moved to FAILED: " + message.getEventType()
                                + " after " + attempts + " attempts: " + cause.getMessage(),
                        String.valueOf(id));
            } else {
                message.reschedule(attempts, now.plus(BACKOFF_BASE.multipliedBy(attempts)));
            }
        });
    }

    /** 供任务开关与监控使用的重试参数 */
    public static int maxAttempts() {
        return MAX_ATTEMPTS;
    }
}
