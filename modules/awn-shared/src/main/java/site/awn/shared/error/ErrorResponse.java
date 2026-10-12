package site.awn.shared.error;

/**
 * 统一错误信封（docs/api/contracts.md）。
 * 未预期异常的 message 由服务端固定文案给出，不透出内部信息。
 */
public record ErrorResponse(String code, String message, String traceId) {

    public static ErrorResponse of(ErrorCode code, String message, String traceId) {
        return new ErrorResponse(code.name(), message, traceId);
    }
}
