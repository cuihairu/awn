package site.awn.shared.error;

import java.util.Map;

/**
 * 统一错误信封（docs/api/contracts.md）。
 * 未预期异常的 message 由服务端固定文案给出，不透出内部信息。
 */
public record ErrorResponse(String code, String message, String traceId, Map<String, String> fieldErrors) {

    public static ErrorResponse of(ErrorCode code, String message, String traceId) {
        return new ErrorResponse(code.name(), message, traceId, Map.of());
    }

    public static ErrorResponse withFieldErrors(
            ErrorCode code, String message, String traceId, Map<String, String> fieldErrors) {
        return new ErrorResponse(code.name(), message, traceId, Map.copyOf(fieldErrors));
    }
}
