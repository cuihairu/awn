package site.awn.order.application;

import org.springframework.stereotype.Service;
import site.awn.order.api.CreateOrderCommand;
import site.awn.order.api.OrderFacade;
import site.awn.order.api.OrderView;

@Service
public class OrderFacadeImpl implements OrderFacade {

    private final OrderWriteService writes;
    private final OrderReadService reads;

    OrderFacadeImpl(OrderWriteService writes, OrderReadService reads) {
        this.writes = writes;
        this.reads = reads;
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
}
