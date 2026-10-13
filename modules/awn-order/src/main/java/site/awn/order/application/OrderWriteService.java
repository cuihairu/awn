package site.awn.order.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.awn.inventory.api.InventoryFacade;
import site.awn.inventory.api.ReserveInventoryCommand;
import site.awn.inventory.api.ReservationView;
import site.awn.marketplace.api.MarketplaceFacade;
import site.awn.order.api.CreateOrderCommand;
import site.awn.order.api.OrderLineCommand;
import site.awn.order.api.OrderView;
import site.awn.order.domain.Order;
import site.awn.order.domain.OrderConflictException;
import site.awn.order.domain.OrderItem;
import site.awn.order.domain.OrderNotFoundException;
import site.awn.order.domain.OrderStatus;
import site.awn.order.domain.IdempotencyConflictException;
import site.awn.order.infrastructure.OrderItemRepository;
import site.awn.order.infrastructure.OrderRepository;
import site.awn.shared.outbox.OutboxMessageRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * 订单写入用例：创建（幂等）、取消、支付确认。
 *
 * <p>创建时序（docs/architecture/inventory-consistency.md）：幂等键加锁判重 →
 * 逐行原子预留库存 → 写订单与明细快照 → 同事务写 Outbox；任一步失败整体回滚，
 * 已成功的预留随事务一并撤销。库存预留不在任何情况下先提交后补偿。</p>
 */
@Service
public class OrderWriteService {

    /** 待支付订单的默认支付截止期；Worker 超时任务据此释放库存 */
    static final Duration DEFAULT_PAYMENT_TTL = Duration.ofMinutes(15);

    private static final String AGGREGATE_TYPE = "ORDER";

    private final OrderRepository orders;
    private final OrderItemRepository items;
    private final InventoryFacade inventory;
    private final MarketplaceFacade marketplace;
    private final OutboxMessageRepository outbox;

    OrderWriteService(OrderRepository orders, OrderItemRepository items,
                      InventoryFacade inventory, MarketplaceFacade marketplace,
                      OutboxMessageRepository outbox) {
        this.orders = orders;
        this.items = items;
        this.inventory = inventory;
        this.marketplace = marketplace;
        this.outbox = outbox;
    }

    @Transactional
    public OrderView createOrder(CreateOrderCommand command) {
        // 1) 幂等键串行化：先到者提交后，后到者在锁内看到已提交订单
        orders.lockIdempotencyKey(command.idempotencyKey());
        String requestHash = requestHash(command);
        Order existing = orders.findByIdempotencyKey(command.idempotencyKey()).orElse(null);
        if (existing != null) {
            if (!existing.getRequestHash().equals(requestHash)) {
                throw new IdempotencyConflictException(
                        "idempotency key already used by a different request: " + command.idempotencyKey());
            }
            return OrderReadService.toView(existing, items.findByOrderId(existing.getId()));
        }

        // 2) 计算金额（服务端计算，禁止浮点；金额为最小货币单位整数）
        long totalAmount = 0;
        long discountAmount = 0;
        long feeAmount = 0;
        for (OrderLineCommand line : command.lines()) {
            long lineTotal = line.lineTotal();
            if (lineTotal < 0) {
                throw new IllegalArgumentException("line total must be non-negative: " + line.productId());
            }
            totalAmount += (long) line.quantity() * line.unitPrice();
            discountAmount += line.discountAmount();
            feeAmount += line.feeAmount();
        }
        long payAmount = totalAmount - discountAmount + feeAmount;
        if (payAmount < 0) {
            throw new IllegalArgumentException("pay amount must be non-negative");
        }

        // 3) 逐行原子预留（orderRef 用订单号；预留与订单同事务提交）
        //    数量型行 → 库存池预留；凭证型行 → 挂牌票品预留（不触碰库存池）
        String orderNo = "ORD-" + UUID.randomUUID();
        List<String> reservationNos = new ArrayList<>(command.lines().size());
        List<String> unitNos = new ArrayList<>(command.lines().size());
        for (OrderLineCommand line : command.lines()) {
            if (line.voucherLine()) {
                unitNos.add(marketplace.reserveUnitForOrder(line.listingNo()));
                reservationNos.add(null);
            } else {
                ReservationView reservation = inventory.reserve(
                        new ReserveInventoryCommand(line.poolCode(), orderNo, line.quantity(), null));
                reservationNos.add(reservation.reservationNo());
                unitNos.add(null);
            }
        }

        // 4) 写订单与明细快照
        Order order = new Order();
        order.setOrderNo(orderNo);
        order.setIdempotencyKey(command.idempotencyKey());
        order.setRequestHash(requestHash);
        order.setUserId(command.userId());
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setCurrency(command.currency());
        order.setTotalAmount(totalAmount);
        order.setDiscountAmount(discountAmount);
        order.setFeeAmount(feeAmount);
        order.setPayAmount(payAmount);
        order.setExpiresAt(Instant.now().plus(DEFAULT_PAYMENT_TTL));
        orders.saveAndFlush(order);

        for (int i = 0; i < command.lines().size(); i++) {
            OrderLineCommand line = command.lines().get(i);
            OrderItem item = new OrderItem();
            item.setOrderId(order.getId());
            item.setPoolCode(line.poolCode());
            item.setProductId(line.productId());
            item.setProductNameSnapshot(line.productNameSnapshot());
            item.setSessionId(line.sessionId());
            item.setTicketCategorySnapshot(line.ticketCategorySnapshot());
            item.setQuantity(line.quantity());
            item.setUnitPrice(line.unitPrice());
            item.setDiscountAmount(line.discountAmount());
            item.setFeeAmount(line.feeAmount());
            item.setLineTotal(line.lineTotal());
            item.setReservationNo(reservationNos.get(i));
            item.setListingNo(line.listingNo());
            item.setUnitNo(unitNos.get(i));
            items.save(item);
        }

        // 5) 同事务写 Outbox：事务提交则事件必然存在，Worker 才不会漏单
        outbox.insertPending(AGGREGATE_TYPE, orderNo, "ORDER_CREATED",
                payload(orderNo, order.getStatus().name(), order.getPayAmount(), order.getCurrency(),
                        order.getUserId()));

        return OrderReadService.toView(order, items.findByOrderId(order.getId()));
    }

    @Transactional
    public boolean cancel(String orderNo) {
        Order order = orders.findByOrderNo(orderNo).orElseThrow(() -> new OrderNotFoundException(orderNo));
        int moved = orders.transition(orderNo, OrderStatus.PENDING_PAYMENT, OrderStatus.CANCELLED);
        if (moved == 1) {
            for (OrderItem item : items.findByOrderId(order.getId())) {
                releaseLine(item);
            }
            outbox.insertPending(AGGREGATE_TYPE, orderNo, "ORDER_CANCELLED",
                    payload(orderNo, OrderStatus.CANCELLED.name(), order.getPayAmount(), order.getCurrency()));
            return true;
        }
        // 迁移未发生：重读后按实际状态处理
        OrderStatus current = orders.findByOrderNo(orderNo).orElseThrow().getStatus();
        if (current == OrderStatus.CANCELLED) {
            return false; // 幂等重放
        }
        throw new OrderConflictException("cancel not allowed from status " + current);
    }

    @Transactional
    public boolean markPaid(String orderNo) {
        Order order = orders.findByOrderNo(orderNo).orElseThrow(() -> new OrderNotFoundException(orderNo));
        int moved = orders.transition(orderNo, OrderStatus.PENDING_PAYMENT, OrderStatus.PAID);
        if (moved == 1) {
            for (OrderItem item : items.findByOrderId(order.getId())) {
                confirmLine(item);
            }
            outbox.insertPending(AGGREGATE_TYPE, orderNo, "ORDER_PAID",
                    payload(orderNo, OrderStatus.PAID.name(), order.getPayAmount(), order.getCurrency(),
                            order.getUserId()));
            return true;
        }
        OrderStatus current = orders.findByOrderNo(orderNo).orElseThrow().getStatus();
        if (current == OrderStatus.PAID) {
            return false; // 幂等重放
        }
        throw new OrderConflictException("payment confirm not allowed from status " + current);
    }

    /**
     * 履约开始联动：PAID → FULFILLING（履约单创建后由履约模块发出 FULFILLMENT_STARTED）。
     * 幂等：已在 FULFILLING / FULFILLED 返回 false。
     */
    @Transactional
    public boolean markFulfilling(String orderNo) {
        Order order = orders.findByOrderNo(orderNo).orElseThrow(() -> new OrderNotFoundException(orderNo));
        int moved = orders.transition(orderNo, OrderStatus.PAID, OrderStatus.FULFILLING);
        if (moved == 1) {
            outbox.insertPending(AGGREGATE_TYPE, orderNo, "ORDER_FULFILLING",
                    payload(orderNo, OrderStatus.FULFILLING.name(), order.getPayAmount(), order.getCurrency()));
            return true;
        }
        OrderStatus current = orders.findByOrderNo(orderNo).orElseThrow().getStatus();
        if (current == OrderStatus.FULFILLING || current == OrderStatus.FULFILLED) {
            return false; // 幂等重放
        }
        throw new OrderConflictException("fulfilling not allowed from status " + current);
    }

    /**
     * 交付完成联动：FULFILLING → FULFILLED（卖家交付 + 平台核验通过后发出 FULFILLMENT_COMPLETED）。
     * 幂等：已是 FULFILLED 返回 false。
     */
    @Transactional
    public boolean markFulfilled(String orderNo) {
        Order order = orders.findByOrderNo(orderNo).orElseThrow(() -> new OrderNotFoundException(orderNo));
        int moved = orders.transition(orderNo, OrderStatus.FULFILLING, OrderStatus.FULFILLED);
        if (moved == 1) {
            outbox.insertPending(AGGREGATE_TYPE, orderNo, "ORDER_FULFILLED",
                    payload(orderNo, OrderStatus.FULFILLED.name(), order.getPayAmount(), order.getCurrency()));
            return true;
        }
        OrderStatus current = orders.findByOrderNo(orderNo).orElseThrow().getStatus();
        if (current == OrderStatus.FULFILLED) {
            return false; // 幂等重放
        }
        throw new OrderConflictException("fulfilled not allowed from status " + current);
    }

    /**
     * 履约失败联动：PAID / FULFILLING / FULFILLED → REFUND_PENDING（退款异常流程入口）。
     * 事件重放幂等：已是 REFUND_PENDING 返回 false。
     */
    @Transactional
    public boolean markRefundPending(String orderNo) {
        orders.findByOrderNo(orderNo).orElseThrow(() -> new OrderNotFoundException(orderNo));
        for (OrderStatus from : new OrderStatus[]{OrderStatus.PAID, OrderStatus.FULFILLING, OrderStatus.FULFILLED}) {
            if (orders.transition(orderNo, from, OrderStatus.REFUND_PENDING) == 1) {
                Order order = orders.findByOrderNo(orderNo).orElseThrow();
                outbox.insertPending(AGGREGATE_TYPE, orderNo, "ORDER_REFUND_PENDING",
                        payload(orderNo, OrderStatus.REFUND_PENDING.name(), order.getPayAmount(), order.getCurrency()));
                return true;
            }
        }
        OrderStatus current = orders.findByOrderNo(orderNo).orElseThrow().getStatus();
        if (current == OrderStatus.REFUND_PENDING) {
            return false; // 幂等重放
        }
        throw new OrderConflictException("refund pending not allowed from status " + current);
    }

    /** 逐行释放：数量型行走库存预留，凭证型行走挂牌票品 */
    private void releaseLine(OrderItem item) {
        if (item.getReservationNo() != null) {
            inventory.release(item.getReservationNo());
        } else {
            marketplace.releaseUnitForOrder(item.getUnitNo());
        }
    }

    /** 逐行确认：数量型行走库存预留，凭证型行走挂牌票品 */
    private void confirmLine(OrderItem item) {
        if (item.getReservationNo() != null) {
            inventory.confirm(item.getReservationNo());
        } else {
            marketplace.confirmUnitForOrder(item.getUnitNo());
        }
    }

    /** 请求指纹：同键同请求 → 原结果；同键不同请求 → 冲突。 */
    private static String requestHash(CreateOrderCommand command) {
        StringBuilder canonical = new StringBuilder();
        canonical.append(command.idempotencyKey()).append('|')
                .append(command.userId()).append('|').append(command.currency());
        command.lines().stream()
                .map(l -> String.join(":",
                        nvl(l.poolCode()), nvl(l.listingNo()), l.productId(),
                        nvl(l.sessionId()), nvl(l.ticketCategorySnapshot()), nvl(l.productNameSnapshot()),
                        String.valueOf(l.quantity()), String.valueOf(l.unitPrice()),
                        String.valueOf(l.discountAmount()), String.valueOf(l.feeAmount())))
                .sorted()
                .forEach(l -> canonical.append('|').append(l));
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String nvl(String value) {
        return value == null ? "" : value;
    }

    /** 极简 JSON 载荷：字段值均为受控字符（订单号/状态/整数/ISO 币种），无需转义 */
    private static String payload(String orderNo, String status, long payAmount, String currency) {
        return "{\"orderNo\":\"" + orderNo + "\",\"status\":\"" + status
                + "\",\"payAmount\":" + payAmount + ",\"currency\":\"" + currency + "\"}";
    }

    /** 带 userId 的载荷：通知等下游消费者据此定位收件人 */
    private static String payload(String orderNo, String status, long payAmount, String currency, long userId) {
        return "{\"orderNo\":\"" + orderNo + "\",\"status\":\"" + status
                + "\",\"payAmount\":" + payAmount + ",\"currency\":\"" + currency
                + "\",\"userId\":" + userId + "}";
    }
}
