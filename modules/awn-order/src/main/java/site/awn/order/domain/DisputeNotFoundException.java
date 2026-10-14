package site.awn.order.domain;

/** 争议单不存在（映射契约错误码 DISPUTE_NOT_FOUND）。 */
public class DisputeNotFoundException extends RuntimeException {

    public DisputeNotFoundException(String disputeNo) {
        super("dispute not found: " + disputeNo);
    }
}
