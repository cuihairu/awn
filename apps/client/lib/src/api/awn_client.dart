import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';

import '../models/event.dart';
import '../models/product.dart';
import '../models/session.dart';
import 'api_exception.dart';

/// 公开只读接口客户端（docs/api/contracts.md「公共接口」）。
///
/// 只覆盖活动 / 场次 / 商品读取面；下单与支付链路待后续增量接入。
/// 错误信封统一解析为 [ApiException]。
class AwnApiClient {
  AwnApiClient({required Dio dio}) : _dio = dio;

  final Dio _dio;

  Future<List<EventView>> listEvents() =>
      _get('/events', _parseList(EventView.fromJson));

  Future<EventView> getEvent(int eventId) =>
      _get('/events/$eventId', (json) => EventView.fromJson(_asMap(json)));

  Future<List<SessionView>> listEventSessions(int eventId) => _get(
      '/events/$eventId/sessions', _parseList(SessionView.fromJson));

  Future<List<ProductListItem>> getSessionProducts(int sessionId) => _get(
      '/sessions/$sessionId/products', _parseList(ProductListItem.fromJson));

  Future<T> _get<T>(String path, T Function(Object? json) parse) async {
    final Response<dynamic> response;
    try {
      response = await _dio.get<dynamic>(path);
    } on DioException catch (error) {
      throw _toApiException(error);
    }
    return parse(response.data);
  }

  ApiException _toApiException(DioException error) {
    final data = error.response?.data;
    if (data is Map && data['code'] is String) {
      return ApiException(
        code: data['code'] as String,
        message: data['message'] is String ? data['message'] as String : '',
      );
    }
    final status = error.response?.statusCode;
    return ApiException(
      code: 'HTTP_${status ?? 0}',
      message: error.message ?? 'request failed',
    );
  }

  static Map<String, dynamic> _asMap(Object? json) =>
      json is Map<String, dynamic> ? json : <String, dynamic>{};

  static List<T> Function(Object?) _parseList<T>(
      T Function(Map<String, dynamic>) fromJson) {
    return (json) {
      if (json is! List) {
        return <T>[];
      }
      return json.map((item) => fromJson(_asMap(item))).toList();
    };
  }
}

/// 测试桩：按路径返回固定 JSON，不触网。
class StubHttpClientAdapter implements HttpClientAdapter {
  StubHttpClientAdapter([Map<String, Object>? responses])
      : _responses = responses ?? {};

  final Map<String, Object> _responses;

  void when(String path, Object payload) => _responses[path] = payload;

  @override
  Future<ResponseBody> fetch(RequestOptions options,
      Stream<Uint8List>? requestStream, Future<void>? cancelFuture) async {
    final payload = _responses[options.path];
    if (payload == null) {
      throw StateError('unexpected request: ${options.path}');
    }
    if (payload is StubFailure) {
      return ResponseBody.fromString(
        jsonEncode(payload.body),
        payload.statusCode,
        headers: {
          Headers.contentTypeHeader: <String>[Headers.jsonContentType],
        },
      );
    }
    return ResponseBody.fromString(
      jsonEncode(payload),
      200,
      headers: {
        Headers.contentTypeHeader: <String>[Headers.jsonContentType],
      },
    );
  }

  @override
  void close({bool force = false}) {}
}

/// 桩用的失败响应载体（带状态码的错误信封），配合 [StubHttpClientAdapter.when]。
class StubFailure {
  StubFailure(this.statusCode, this.body);

  final int statusCode;
  final Object body;
}
