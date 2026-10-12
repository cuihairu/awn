package site.awn.fulfillment.domain;

/** 履约单不存在 */
public class FulfillmentNotFoundException extends RuntimeException {

    public FulfillmentNotFoundException(String message) {
        super(message);
    }
}
