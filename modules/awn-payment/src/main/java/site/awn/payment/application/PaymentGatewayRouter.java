package site.awn.payment.application;

import org.springframework.stereotype.Component;
import site.awn.payment.api.PaymentGateway;
import site.awn.payment.domain.PaymentConflictException;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 渠道路由：按渠道标识定位网关实现。 */
@Component
public class PaymentGatewayRouter {

    private final Map<String, PaymentGateway> gateways;

    public PaymentGatewayRouter(List<PaymentGateway> gateways) {
        this.gateways = gateways.stream()
                .collect(Collectors.toUnmodifiableMap(PaymentGateway::channelCode, Function.identity()));
    }

    public PaymentGateway forChannel(String channel) {
        PaymentGateway gateway = gateways.get(channel);
        if (gateway == null) {
            throw new PaymentConflictException("unsupported payment channel: " + channel);
        }
        return gateway;
    }

    /** 可用渠道编码（字母序）：由已注册网关决定，客户端不硬编码渠道名 */
    public List<String> channels() {
        return gateways.keySet().stream().sorted().toList();
    }
}
