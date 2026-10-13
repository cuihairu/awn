package site.awn.fulfillment.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.awn.fulfillment.api.DeliverCommand;
import site.awn.fulfillment.api.FulfillmentFacade;
import site.awn.fulfillment.api.FulfillmentView;
import site.awn.fulfillment.api.VerifyDeliveryCommand;
import site.awn.fulfillment.domain.DeliveryRecord;
import site.awn.fulfillment.domain.DeliveryResult;
import site.awn.fulfillment.domain.Fulfillment;
import site.awn.fulfillment.domain.FulfillmentConflictException;
import site.awn.fulfillment.domain.FulfillmentNotFoundException;
import site.awn.fulfillment.infrastructure.DeliveryRecordRepository;
import site.awn.fulfillment.infrastructure.FulfillmentRepository;
import site.awn.shared.outbox.OutboxMessageRepository;
import site.awn.shared.url.StorageUrlValidator;

import java.util.UUID;

/**
 * 交付流程与异常处理。
 * 履约失败（核验拒绝 / 卖家取消 / 异常上报）一律转 FAILED 并写
 * FULFILLMENT_FAILED 事件——进入异常流程（退款等）而非静默吞掉。
 */
@Service
public class FulfillmentFacadeImpl implements FulfillmentFacade {

    private static final String AGGREGATE_TYPE = "fulfillment";

    private final FulfillmentRepository fulfillments;
    private final DeliveryRecordRepository deliveries;
    private final OutboxMessageRepository outbox;

    public FulfillmentFacadeImpl(FulfillmentRepository fulfillments,
                                 DeliveryRecordRepository deliveries,
                                 OutboxMessageRepository outbox) {
        this.fulfillments = fulfillments;
        this.deliveries = deliveries;
        this.outbox = outbox;
    }

    @Override
    @Transactional(readOnly = true)
    public FulfillmentView fulfillmentOf(String orderNo) {
        return fulfillments.findByOrderNo(orderNo).map(this::toView)
                .orElseThrow(() -> new FulfillmentNotFoundException("fulfillment not found for order: " + orderNo));
    }

    @Override
    @Transactional
    public FulfillmentView deliverSeller(DeliverCommand command) {
        if (command.evidenceUrl() == null || command.evidenceUrl().isBlank()) {
            throw new IllegalArgumentException("evidence url is required");
        }
        // 交付凭证仅白名单存储域
        StorageUrlValidator.validate(command.evidenceUrl());
        Fulfillment fulfillment = fulfillmentOfOrder(command.orderNo());
        fulfillment.markDelivered();
        deliveries.save(new DeliveryRecord(fulfillment.getFulfillmentNo(), command.evidenceUrl()));
        return toView(fulfillment);
    }

    @Override
    @Transactional
    public FulfillmentView verifyDelivery(VerifyDeliveryCommand command) {
        Fulfillment fulfillment = fulfillmentOfOrder(command.orderNo());
        DeliveryRecord record = deliveries
                .findFirstByFulfillmentNoAndResultOrderByDeliveredAtAsc(
                        fulfillment.getFulfillmentNo(), DeliveryResult.PENDING)
                .orElseThrow(() -> new FulfillmentConflictException(
                        "no pending delivery to verify for order: " + command.orderNo()));
        record.conclude(command.passed() ? DeliveryResult.PASSED : DeliveryResult.REJECTED, command.note());
        if (command.passed()) {
            fulfillment.verifyPassed();
        } else {
            fulfillment.verifyRejected(command.note() == null ? "delivery verification rejected" : command.note());
            emitFailure(fulfillment);
        }
        return toView(fulfillment);
    }

    @Override
    @Transactional
    public FulfillmentView reportFailure(String orderNo, String reason) {
        Fulfillment fulfillment = fulfillmentOfOrder(orderNo);
        fulfillment.markFailed(reason == null ? "fulfillment failure reported" : reason);
        emitFailure(fulfillment);
        return toView(fulfillment);
    }

    @Override
    @Transactional
    public FulfillmentView cancel(String orderNo, String reason) {
        Fulfillment fulfillment = fulfillmentOfOrder(orderNo);
        fulfillment.cancel(reason == null ? "order cancelled" : reason);
        return toView(fulfillment);
    }

    /** 履约失败必须可见：FULFILLMENT_FAILED 事件进入异常流程（退款 / 人工介入） */
    private void emitFailure(Fulfillment fulfillment) {
        outbox.insertPending(AGGREGATE_TYPE, fulfillment.getFulfillmentNo(),
                "FULFILLMENT_FAILED", payload(fulfillment));
    }

    private static String payload(Fulfillment fulfillment) {
        return "{\"fulfillmentNo\":\"" + fulfillment.getFulfillmentNo()
                + "\",\"orderNo\":\"" + fulfillment.getOrderNo()
                + "\",\"status\":\"FAILED\""
                + ",\"reason\":" + jsonString(fulfillment.getFailReason()) + "}";
    }

    private static String jsonString(String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private Fulfillment fulfillmentOfOrder(String orderNo) {
        return fulfillments.findByOrderNo(orderNo)
                .orElseThrow(() -> new FulfillmentNotFoundException("fulfillment not found for order: " + orderNo));
    }

    private FulfillmentView toView(Fulfillment f) {
        return new FulfillmentView(f.getFulfillmentNo(), f.getOrderNo(), f.getStatus().name(), f.getFailReason());
    }
}
