package site.awn.order.domain;

/** 订单不存在。对应 404 语义。 */
public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException(String orderNo) {
        super("order not found: " + orderNo);
    }
}
