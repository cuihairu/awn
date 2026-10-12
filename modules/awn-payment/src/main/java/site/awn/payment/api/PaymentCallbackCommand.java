package site.awn.payment.api;

import java.util.Map;

/** 支付回调命令：原始报文与头用于验签；金额/币种用于核对。 */
public record PaymentCallbackCommand(
        String channel,
        String rawBody,
        Map<String, String> headers,
        String channelTxnNo,
        String currency,
        long amount,
        boolean success
) {
    public PaymentCallbackCommand {
        headers = headers == null ? Map.of() : Map.copyOf(headers);
    }
}
