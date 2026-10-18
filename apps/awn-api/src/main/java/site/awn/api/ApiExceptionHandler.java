package site.awn.api;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import site.awn.campaign.domain.CampaignNotFoundException;
import site.awn.inventory.domain.SeatNotFoundException;
import site.awn.catalog.domain.ProductNotFoundException;
import site.awn.event.domain.EventConflictException;
import site.awn.event.domain.EventNotFoundException;
import site.awn.event.domain.SessionNotFoundException;
import site.awn.event.domain.VenueNotFoundException;
import site.awn.fulfillment.domain.FulfillmentConflictException;
import site.awn.fulfillment.domain.FulfillmentNotFoundException;
import site.awn.marketplace.domain.MarketplaceConflictException;
import site.awn.marketplace.domain.MarketplaceNotFoundException;
import site.awn.inventory.domain.InsufficientInventoryException;
import site.awn.inventory.domain.InventoryConflictException;
import site.awn.inventory.domain.PoolNotFoundException;
import site.awn.payment.domain.PaymentCallbackRejectedException;
import site.awn.payment.domain.PaymentConflictException;
import site.awn.payment.domain.PaymentNotFoundException;
import site.awn.payment.domain.RefundConflictException;
import site.awn.payment.domain.RefundNotFoundException;
import site.awn.order.domain.IdempotencyConflictException;
import site.awn.order.domain.OrderConflictException;
import site.awn.order.domain.OrderNotFoundException;
import site.awn.order.domain.DisputeNotFoundException;
import site.awn.order.domain.OrderDisputeConflictException;
import site.awn.order.domain.WaitlistConflictException;
import site.awn.order.domain.WaitlistNotFoundException;
import site.awn.shared.error.ErrorCode;
import site.awn.star.domain.PersonNotFoundException;
import site.awn.star.domain.StarConflictException;
import site.awn.star.domain.WorkNotFoundException;
import site.awn.shared.error.ErrorResponse;
import site.awn.shared.trace.TraceId;

/**
 * 全局异常处理（组合根）：所有非 2xx 响应统一为错误信封
 * （docs/api/contracts.md「错误信封与错误码」）。
 * 模块只定义领域异常，HTTP 语义在此一处映射；
 * 已知业务异常透出可读 message，未预期异常只返回固定文案，堆栈仅进日志。
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /** 未预期异常的固定文案：不透出任何内部信息 */
    private static final String INTERNAL_MESSAGE = "internal server error";

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<ErrorResponse> idempotencyConflict(IdempotencyConflictException e) {
        return envelope(ErrorCode.IDEMPOTENCY_CONFLICT, e.getMessage());
    }

    @ExceptionHandler(OrderConflictException.class)
    public ResponseEntity<ErrorResponse> orderConflict(OrderConflictException e) {
        return envelope(ErrorCode.ORDER_CONFLICT, e.getMessage());
    }

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ErrorResponse> orderNotFound(OrderNotFoundException e) {
        return envelope(ErrorCode.ORDER_NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(PoolNotFoundException.class)
    public ResponseEntity<ErrorResponse> poolNotFound(PoolNotFoundException e) {
        return envelope(ErrorCode.INVENTORY_POOL_NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(EventNotFoundException.class)
    public ResponseEntity<ErrorResponse> eventNotFound(EventNotFoundException e) {
        return envelope(ErrorCode.EVENT_NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(SessionNotFoundException.class)
    public ResponseEntity<ErrorResponse> sessionNotFound(SessionNotFoundException e) {
        return envelope(ErrorCode.SESSION_NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(VenueNotFoundException.class)
    public ResponseEntity<ErrorResponse> venueNotFound(VenueNotFoundException e) {
        return envelope(ErrorCode.VENUE_NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(WaitlistNotFoundException.class)
    public ResponseEntity<ErrorResponse> waitlistNotFound(WaitlistNotFoundException e) {
        return envelope(ErrorCode.WAITLIST_NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(WaitlistConflictException.class)
    public ResponseEntity<ErrorResponse> waitlistConflict(WaitlistConflictException e) {
        return envelope(ErrorCode.WAITLIST_CONFLICT, e.getMessage());
    }

    @ExceptionHandler(DisputeNotFoundException.class)
    public ResponseEntity<ErrorResponse> disputeNotFound(DisputeNotFoundException e) {
        return envelope(ErrorCode.DISPUTE_NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(SeatNotFoundException.class)
    public ResponseEntity<ErrorResponse> seatNotFound(SeatNotFoundException e) {
        return envelope(ErrorCode.SEAT_NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(OrderDisputeConflictException.class)
    public ResponseEntity<ErrorResponse> disputeConflict(OrderDisputeConflictException e) {
        return envelope(ErrorCode.DISPUTE_CONFLICT, e.getMessage());
    }

    @ExceptionHandler(EventConflictException.class)
    public ResponseEntity<ErrorResponse> eventConflict(EventConflictException e) {
        return envelope(ErrorCode.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(CampaignNotFoundException.class)
    public ResponseEntity<ErrorResponse> campaignNotFound(CampaignNotFoundException e) {
        return envelope(ErrorCode.CAMPAIGN_NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(PaymentNotFoundException.class)
    public ResponseEntity<ErrorResponse> paymentNotFound(PaymentNotFoundException e) {
        return envelope(ErrorCode.PAYMENT_NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(RefundNotFoundException.class)
    public ResponseEntity<ErrorResponse> refundNotFound(RefundNotFoundException e) {
        return envelope(ErrorCode.REFUND_NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(PaymentConflictException.class)
    public ResponseEntity<ErrorResponse> paymentConflict(PaymentConflictException e) {
        return envelope(ErrorCode.PAYMENT_CONFLICT, e.getMessage());
    }

    @ExceptionHandler(PaymentCallbackRejectedException.class)
    public ResponseEntity<ErrorResponse> paymentCallbackRejected(PaymentCallbackRejectedException e) {
        return envelope(ErrorCode.PAYMENT_CALLBACK_REJECTED, e.getMessage());
    }

    @ExceptionHandler(RefundConflictException.class)
    public ResponseEntity<ErrorResponse> refundConflict(RefundConflictException e) {
        return envelope(ErrorCode.REFUND_CONFLICT, e.getMessage());
    }

    @ExceptionHandler(MarketplaceNotFoundException.class)
    public ResponseEntity<ErrorResponse> marketplaceNotFound(MarketplaceNotFoundException e) {
        return envelope(ErrorCode.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(MarketplaceConflictException.class)
    public ResponseEntity<ErrorResponse> marketplaceConflict(MarketplaceConflictException e) {
        return envelope(ErrorCode.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(PersonNotFoundException.class)
    public ResponseEntity<ErrorResponse> personNotFound(PersonNotFoundException e) {
        return envelope(ErrorCode.PERSON_NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(WorkNotFoundException.class)
    public ResponseEntity<ErrorResponse> workNotFound(WorkNotFoundException e) {
        return envelope(ErrorCode.WORK_NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(StarConflictException.class)
    public ResponseEntity<ErrorResponse> starConflict(StarConflictException e) {
        return envelope(ErrorCode.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(FulfillmentNotFoundException.class)
    public ResponseEntity<ErrorResponse> fulfillmentNotFound(FulfillmentNotFoundException e) {
        return envelope(ErrorCode.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(FulfillmentConflictException.class)
    public ResponseEntity<ErrorResponse> fulfillmentConflict(FulfillmentConflictException e) {
        return envelope(ErrorCode.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(InsufficientInventoryException.class)
    public ResponseEntity<ErrorResponse> insufficientInventory(InsufficientInventoryException e) {
        return envelope(ErrorCode.INSUFFICIENT_INVENTORY, e.getMessage());
    }

    @ExceptionHandler(InventoryConflictException.class)
    public ResponseEntity<ErrorResponse> inventoryConflict(InventoryConflictException e) {
        return envelope(ErrorCode.INVENTORY_CONFLICT, e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> illegalArgument(IllegalArgumentException e) {
        return envelope(ErrorCode.VALIDATION_FAILED, e.getMessage());
    }

    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<ErrorResponse> productNotFound(ProductNotFoundException e) {
        return envelope(ErrorCode.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> methodArgumentNotValid(MethodArgumentNotValidException e) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        String message = "validation failed";
        return ResponseEntity.status(HttpStatus.valueOf(ErrorCode.VALIDATION_FAILED.httpStatus()))
                .body(ErrorResponse.withFieldErrors(
                        ErrorCode.VALIDATION_FAILED, message, TraceId.current(), fieldErrors));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> typeMismatch(MethodArgumentTypeMismatchException e) {
        return envelope(ErrorCode.VALIDATION_FAILED, "invalid value for " + e.getName());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> accessDenied(ResponseStatusException e) {
        ErrorCode code = e.getStatusCode().value() == HttpStatus.UNAUTHORIZED.value()
                ? ErrorCode.UNAUTHORIZED : ErrorCode.ACCESS_DENIED;
        return envelope(code, e.getReason() == null ? "access denied" : e.getReason());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> notReadable(HttpMessageNotReadableException e) {
        return envelope(ErrorCode.VALIDATION_FAILED, "request body is not readable");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> unexpected(Exception e) {
        // 堆栈只进日志，客户端只收到固定文案
        log.error("unhandled exception", e);
        return envelope(ErrorCode.INTERNAL_ERROR, INTERNAL_MESSAGE);
    }

    private ResponseEntity<ErrorResponse> envelope(ErrorCode code, String message) {
        return ResponseEntity.status(HttpStatus.valueOf(code.httpStatus()))
                .body(ErrorResponse.of(code, message, TraceId.current()));
    }
}
