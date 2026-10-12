package site.awn.payment.api;

import java.util.Map;

/** 退款回调命令。 */
public record RefundCallbackCommand(
        String channel,
        String rawBody,
        Map<String, String> headers,
        String channelRefundNo,
        boolean success
) {
    public RefundCallbackCommand {
        headers = headers == null ? Map.of() : Map.copyOf(headers);
    }
}
