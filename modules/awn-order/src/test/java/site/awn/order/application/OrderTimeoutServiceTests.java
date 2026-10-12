package site.awn.order.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import site.awn.inventory.api.InventoryFacade;
import site.awn.order.api.OrderTimeoutResult;
import site.awn.order.domain.Order;
import site.awn.order.domain.OrderItem;
import site.awn.order.domain.OrderStatus;
import site.awn.order.infrastructure.OrderItemRepository;
import site.awn.order.infrastructure.OrderRepository;
import site.awn.shared.outbox.OutboxMessage;
import site.awn.shared.outbox.OutboxMessageRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 订单超时释放的裁决逻辑（纯单元，无 DB）：
 * 与支付确认竞态时由守卫迁移裁决，输掉的路径 skipped 且绝不释放库存；
 * 单订单异常进 failed 清单，不阻塞整批。
 */
class OrderTimeoutServiceTests {

    private OrderRepository orders;
    private OrderItemRepository items;
    private InventoryFacade inventory;
    private OutboxMessageRepository outbox;
    private TransactionTemplate transactions;
    private OrderTimeoutService service;

    @BeforeEach
    void setUp() {
        orders = mock(OrderRepository.class);
        items = mock(OrderItemRepository.class);
        inventory = mock(InventoryFacade.class);
        outbox = mock(OutboxMessageRepository.class);
        transactions = mock(TransactionTemplate.class);
        // TransactionTemplate 透传回调，保持单订单事务语义由真实模板保证
        when(transactions.execute(any())).thenAnswer(invocation ->
                ((TransactionCallback<Boolean>) invocation.getArgument(0)).doInTransaction(null));
        service = new OrderTimeoutService(orders, items, inventory, outbox, transactions);
    }

    private Order order(long id, String orderNo, OrderStatus status) {
        Order order = mock(Order.class);
        when(order.getId()).thenReturn(id);
        when(order.getOrderNo()).thenReturn(orderNo);
        when(order.getStatus()).thenReturn(status);
        when(order.getPayAmount()).thenReturn(20000L);
        when(order.getCurrency()).thenReturn("CNY");
        return order;
    }

    @Test
    void expiredOrderTransitionsReleasesStockAndPublishesEvent() {
        when(orders.findExpiredPendingOrderNos(any(Instant.class), eq(OrderStatus.PENDING_PAYMENT), any()))
                .thenReturn(List.of("TO-1"));
        Order pending = order(42L, "TO-1", OrderStatus.PENDING_PAYMENT);
        when(orders.findByOrderNo("TO-1")).thenReturn(Optional.of(pending));
        when(orders.transition("TO-1", OrderStatus.PENDING_PAYMENT, OrderStatus.EXPIRED)).thenReturn(1);
        OrderItem item = mock(OrderItem.class);
        when(item.getReservationNo()).thenReturn("R-1");
        when(items.findByOrderId(42L)).thenReturn(List.of(item));

        OrderTimeoutResult result = service.releaseExpiredOrders(Instant.now());

        assertThat(result.expired()).isEqualTo(1);
        assertThat(result.skipped()).isZero();
        assertThat(result.failed()).isEmpty();
        verify(inventory).release("R-1");
        ArgumentCaptor<OutboxMessage> event = ArgumentCaptor.forClass(OutboxMessage.class);
        verify(outbox).save(event.capture());
        assertThat(event.getValue().getEventType()).isEqualTo("ORDER_EXPIRED");
        assertThat(event.getValue().getAggregateType()).isEqualTo("ORDER");
        assertThat(event.getValue().getAggregateId()).isEqualTo("TO-1");
        assertThat(event.getValue().getPayload()).contains("TO-1").contains("EXPIRED");
    }

    @Test
    void raceLoserIsSkippedWithoutReleasingStock() {
        // 查询时仍待支付、迁移时已被支付确认抢先：守卫返回 0，必须放弃释放
        when(orders.findExpiredPendingOrderNos(any(Instant.class), eq(OrderStatus.PENDING_PAYMENT), any()))
                .thenReturn(List.of("TO-2"));
        Order paid = order(42L, "TO-2", OrderStatus.PAID);
        when(orders.findByOrderNo("TO-2")).thenReturn(Optional.of(paid));
        when(orders.transition("TO-2", OrderStatus.PENDING_PAYMENT, OrderStatus.EXPIRED)).thenReturn(0);

        OrderTimeoutResult result = service.releaseExpiredOrders(Instant.now());

        assertThat(result.expired()).isZero();
        assertThat(result.skipped()).isEqualTo(1);
        assertThat(result.failed()).isEmpty();
        verify(inventory, never()).release(any());
        verify(outbox, never()).save(any());
    }

    @Test
    void singleFailureIsReportedAndBatchContinues() {
        when(orders.findExpiredPendingOrderNos(any(Instant.class), eq(OrderStatus.PENDING_PAYMENT), any()))
                .thenReturn(List.of("TO-BAD", "TO-OK"));
        Order bad = order(42L, "TO-BAD", OrderStatus.PENDING_PAYMENT);
        when(orders.findByOrderNo("TO-BAD")).thenReturn(Optional.of(bad));
        when(orders.transition("TO-BAD", OrderStatus.PENDING_PAYMENT, OrderStatus.EXPIRED)).thenReturn(1);
        OrderItem badItem = mock(OrderItem.class);
        when(badItem.getReservationNo()).thenReturn("R-BAD");
        when(items.findByOrderId(42L)).thenReturn(List.of(badItem));
        when(inventory.release("R-BAD")).thenThrow(new IllegalStateException("boom"));

        Order ok = order(43L, "TO-OK", OrderStatus.PENDING_PAYMENT);
        when(orders.findByOrderNo("TO-OK")).thenReturn(Optional.of(ok));
        when(orders.transition("TO-OK", OrderStatus.PENDING_PAYMENT, OrderStatus.EXPIRED)).thenReturn(1);
        OrderItem okItem = mock(OrderItem.class);
        when(okItem.getReservationNo()).thenReturn("R-OK");
        when(items.findByOrderId(43L)).thenReturn(List.of(okItem));

        OrderTimeoutResult result = service.releaseExpiredOrders(Instant.now());

        assertThat(result.expired()).isEqualTo(1);
        assertThat(result.failed()).containsExactly("TO-BAD");
        verify(inventory).release("R-OK");
    }

    @Test
    void emptyBatchYieldsEmptyResult() {
        when(orders.findExpiredPendingOrderNos(any(Instant.class), eq(OrderStatus.PENDING_PAYMENT), any()))
                .thenReturn(List.of());

        OrderTimeoutResult result = service.releaseExpiredOrders(Instant.now());

        assertThat(result.expired()).isZero();
        assertThat(result.skipped()).isZero();
        assertThat(result.failed()).isEmpty();
    }
}
