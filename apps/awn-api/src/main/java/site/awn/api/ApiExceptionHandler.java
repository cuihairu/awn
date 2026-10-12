package site.awn.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import site.awn.catalog.domain.ProductNotFoundException;
import site.awn.inventory.domain.InsufficientInventoryException;
import site.awn.inventory.domain.InventoryConflictException;
import site.awn.inventory.domain.PoolNotFoundException;
import site.awn.order.domain.IdempotencyConflictException;
import site.awn.order.domain.OrderConflictException;
import site.awn.order.domain.OrderNotFoundException;
import site.awn.shared.error.ErrorCode;
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
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .findFirst()
                .orElse("validation failed");
        return envelope(ErrorCode.VALIDATION_FAILED, message);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> typeMismatch(MethodArgumentTypeMismatchException e) {
        return envelope(ErrorCode.VALIDATION_FAILED, "invalid value for " + e.getName());
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
