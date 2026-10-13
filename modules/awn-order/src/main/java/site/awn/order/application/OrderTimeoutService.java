package site.awn.order.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import site.awn.inventory.api.InventoryFacade;
import site.awn.order.api.OrderTimeoutResult;
import site.awn.order.domain.Order;
import site.awn.order.domain.OrderStatus;
import site.awn.order.infrastructure.OrderItemRepository;
import site.awn.order.infrastructure.OrderRepository;
import site.awn.shared.outbox.OutboxMessageRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 订单超时释放（Worker 调用）。
 *
 * <p>与支付确认的竞态由守卫迁移裁决：迁移成功才释放库存，输掉的路径
 * 记为 skipped（订单已支付/已取消，库存合法保留），绝不盲目释放。
 * 单订单独立事务，单个异常订单只进 failed 清单，不阻塞整批。</p>
 */
@Service
public class OrderTimeoutService {

    private static final Logger log = LoggerFactory.getLogger(OrderTimeoutService.class);

    private static final String AGGREGATE_TYPE = "ORDER";

    /** 单轮批量上限：防止一次性锁住过多订单 */
    private static final int BATCH_LIMIT = 200;

    private final OrderRepository orders;
    private final OrderItemRepository items;
    private final InventoryFacade inventory;
    private final OutboxMessageRepository outbox;
    private final TransactionTemplate transactions;

    OrderTimeoutService(OrderRepository orders, OrderItemRepository items,
                        InventoryFacade inventory, OutboxMessageRepository outbox,
                        TransactionTemplate transactions) {
        this.orders = orders;
        this.items = items;
        this.inventory = inventory;
        this.outbox = outbox;
        this.transactions = transactions;
    }

    public OrderTimeoutResult releaseExpiredOrders(Instant now) {
        List<String> orderNos = orders.findExpiredPendingOrderNos(
                now, OrderStatus.PENDING_PAYMENT, Pageable.ofSize(BATCH_LIMIT));
        int expired = 0;
        int skipped = 0;
        List<String> failed = new ArrayList<>();
        for (String orderNo : orderNos) {
            try {
                Boolean released = transactions.execute(status -> releaseOne(orderNo));
                if (Boolean.TRUE.equals(released)) {
                    expired++;
                } else {
                    skipped++;
                }
            } catch (RuntimeException e) {
                // 异常路径显式上报，不允许静默吞掉（巡检红线：不许只报在做）
                log.error("order timeout release failed: {}", orderNo, e);
                failed.add(orderNo);
            }
        }
        return new OrderTimeoutResult(expired, skipped, failed);
    }

    /** 单订单释放，运行在独立事务中 */
    private boolean releaseOne(String orderNo) {
        Order order = orders.findByOrderNo(orderNo).orElseThrow();
        int moved = orders.transition(orderNo, OrderStatus.PENDING_PAYMENT, OrderStatus.EXPIRED);
        if (moved == 0) {
            // 竞态输家：重读实际状态。EXPIRED 为幂等重放，其余状态（PAID/CANCELLED）合法保留库存
            return false;
        }
        Order fresh = orders.findByOrderNo(orderNo).orElseThrow();
        items.findByOrderId(fresh.getId()).forEach(item -> inventory.release(item.getReservationNo()));
        outbox.insertPending(AGGREGATE_TYPE, orderNo, "ORDER_EXPIRED",
                "{\"orderNo\":\"" + orderNo + "\",\"status\":\"EXPIRED\",\"payAmount\":"
                        + order.getPayAmount() + ",\"currency\":\"" + order.getCurrency() + "\"}");
        return true;
    }
}
