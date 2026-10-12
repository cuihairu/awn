package site.awn.payment.sandbox;

import org.springframework.stereotype.Component;
import site.awn.payment.api.GatewayChannelStatus;
import site.awn.payment.api.GatewayChargeCommand;
import site.awn.payment.api.GatewayChargeResult;
import site.awn.payment.api.GatewayQueryResult;
import site.awn.payment.api.GatewayRefundCommand;
import site.awn.payment.api.GatewayRefundResult;
import site.awn.payment.api.PaymentGateway;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 沙箱渠道（测试适配器）：模拟成功 / 超时（结果未知）/ 失败。
 * 渠道状态保存在内存中，供回调与对账查询模拟真实渠道侧事实。
 */
@Component
public class SandboxPaymentGateway implements PaymentGateway {

    public static final String CHANNEL = "sandbox";

    /** 验签头：沙箱约定 body 的 HMAC 简化为固定头值 */
    public static final String SIGNATURE_HEADER = "X-Sandbox-Signature";

    private final Map<String, AtomicReference<GatewayChannelStatus>> charges = new ConcurrentHashMap<>();
    private final Map<String, AtomicReference<GatewayChannelStatus>> refunds = new ConcurrentHashMap<>();

    @Override
    public String channelCode() {
        return CHANNEL;
    }

    @Override
    public GatewayChargeResult createCharge(GatewayChargeCommand command) {
        String channelTxnNo = "SBX-TXN-" + UUID.randomUUID();
        charges.put(channelTxnNo, new AtomicReference<>(GatewayChannelStatus.PENDING));
        return new GatewayChargeResult(channelTxnNo, GatewayChannelStatus.PENDING);
    }

    @Override
    public GatewayRefundResult createRefund(GatewayRefundCommand command) {
        String channelRefundNo = "SBX-RFD-" + UUID.randomUUID();
        refunds.put(channelRefundNo, new AtomicReference<>(GatewayChannelStatus.PENDING));
        return new GatewayRefundResult(channelRefundNo, GatewayChannelStatus.PENDING);
    }

    @Override
    public GatewayQueryResult queryCharge(String channelTxnNo) {
        AtomicReference<GatewayChannelStatus> state = charges.get(channelTxnNo);
        GatewayChannelStatus status = state != null ? state.get() : GatewayChannelStatus.UNKNOWN;
        return new GatewayQueryResult(channelTxnNo, status);
    }

    @Override
    public GatewayQueryResult queryRefund(String channelRefundNo) {
        AtomicReference<GatewayChannelStatus> state = refunds.get(channelRefundNo);
        GatewayChannelStatus status = state != null ? state.get() : GatewayChannelStatus.UNKNOWN;
        return new GatewayQueryResult(channelRefundNo, status);
    }

    /**
     * 沙箱验签：headers 中 X-Sandbox-Signature 必须等于固定值。
     * 真实渠道应使用 HMAC + 时间戳防重放；沙箱只验证路径存在性。
     */
    @Override
    public boolean verifyCallback(String rawBody, Map<String, String> headers) {
        return "awn-sandbox".equals(headers.get(SIGNATURE_HEADER));
    }

    // ------------------------------------------------------------------
    // 沙箱控制面：测试用例据此后推渠道侧事实（成功 / 失败 / 结果未知）
    // ------------------------------------------------------------------

    /** 渠道侧标记交易成功（模拟用户完成支付） */
    public void succeedCharge(String channelTxnNo) {
        setCharge(channelTxnNo, GatewayChannelStatus.SUCCEEDED);
    }

    /** 渠道侧标记交易失败 */
    public void failCharge(String channelTxnNo) {
        setCharge(channelTxnNo, GatewayChannelStatus.FAILED);
    }

    /** 渠道侧结果未知（模拟超时，对账应发现） */
    public void unknownCharge(String channelTxnNo) {
        setCharge(channelTxnNo, GatewayChannelStatus.UNKNOWN);
    }

    public void succeedRefund(String channelRefundNo) {
        AtomicReference<GatewayChannelStatus> state = refunds.get(channelRefundNo);
        if (state != null) {
            state.set(GatewayChannelStatus.SUCCEEDED);
        }
    }

    private void setCharge(String channelTxnNo, GatewayChannelStatus status) {
        AtomicReference<GatewayChannelStatus> state = charges.get(channelTxnNo);
        if (state == null) {
            throw new IllegalArgumentException("unknown sandbox txn: " + channelTxnNo);
        }
        state.set(status);
    }
}
