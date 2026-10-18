package site.awn.order.application;

import org.springframework.stereotype.Service;
import site.awn.order.api.CreateOrderCommand;
import site.awn.order.api.OrderFacade;
import site.awn.order.api.OrderTimeoutResult;
import site.awn.order.api.OrderView;
import site.awn.order.api.QuoteCommand;
import site.awn.order.api.QuoteView;

import java.time.Instant;

@Service
public class OrderFacadeImpl implements OrderFacade {

    private final OrderWriteService writes;
    private final OrderReadService reads;
    private final OrderTimeoutService timeouts;

    OrderFacadeImpl(OrderWriteService writes, OrderReadService reads, OrderTimeoutService timeouts) {
        this.writes = writes;
        this.reads = reads;
        this.timeouts = timeouts;
    }

    @Override
    public OrderView createOrder(CreateOrderCommand command) {
        return writes.createOrder(command);
    }

    @Override
    public OrderView createWaitlistPromotionOrder(CreateOrderCommand command) {
        return writes.createWaitlistPromotionOrder(command);
    }

    @Override
    public OrderView orderOf(String orderNo) {
        return reads.orderOf(orderNo);
    }

    @Override
    public long ownerOf(String orderNo) {
        return reads.ownerOf(orderNo);
    }

    /** 报价：纯计算，不预留库存、不落库 */
    @Override
    public QuoteView quote(QuoteCommand command) {
        OrderWriteService.Amounts amounts = OrderWriteService.computeAmounts(command.lines());
        return new QuoteView(command.currency(), amounts.totalAmount(), amounts.discountAmount(),
                amounts.feeAmount(), amounts.payAmount());
    }

    @Override
    public java.util.List<OrderView> recentOrders() {
        return reads.recentOrders();
    }

    @Override
    public boolean cancel(String orderNo) {
        return writes.cancel(orderNo);
    }

    @Override
    public boolean markPaid(String orderNo) {
        return writes.markPaid(orderNo);
    }

    @Override
    public boolean markFulfilling(String orderNo) {
        return writes.markFulfilling(orderNo);
    }

    @Override
    public boolean markFulfilled(String orderNo) {
        return writes.markFulfilled(orderNo);
    }

    @Override
    public boolean markRefundPending(String orderNo) {
        return writes.markRefundPending(orderNo);
    }

    @Override
    public OrderTimeoutResult releaseExpiredOrders(Instant now) {
        return timeouts.releaseExpiredOrders(now);
    }
}
