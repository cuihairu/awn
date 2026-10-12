package site.awn.order.application;

import org.springframework.stereotype.Service;
import site.awn.order.api.CreateOrderCommand;
import site.awn.order.api.OrderFacade;
import site.awn.order.api.OrderTimeoutResult;
import site.awn.order.api.OrderView;

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
    public OrderView orderOf(String orderNo) {
        return reads.orderOf(orderNo);
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
    public boolean markRefundPending(String orderNo) {
        return writes.markRefundPending(orderNo);
    }

    @Override
    public OrderTimeoutResult releaseExpiredOrders(Instant now) {
        return timeouts.releaseExpiredOrders(now);
    }
}
