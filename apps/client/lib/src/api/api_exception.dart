/// 统一错误信封异常（docs/api/contracts.md「错误信封与错误码」）。
///
/// 后端非 2xx 一律返回 `{code, message, traceId}`；客户端据此分支处理，
/// 不得自行推断错误码与 HTTP 状态的映射。
class ApiException implements Exception {
  ApiException({required this.code, required this.message});

  /// 机器可读错误码（EVENT_NOT_FOUND / SESSION_NOT_FOUND / RATE_LIMITED 等）。
  final String code;
  final String message;

  @override
  String toString() => 'ApiException($code): $message';
}
