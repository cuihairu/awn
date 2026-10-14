import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:awn_client/src/api/api_exception.dart';
import 'package:awn_client/src/api/awn_client.dart';

/// 公开只读端点客户端：路径、解析与错误信封（docs/api/contracts.md）。
AwnApiClient _clientWith(StubHttpClientAdapter adapter) {
  final dio = Dio(BaseOptions(
    baseUrl: 'http://stub/api/v1',
  ));
  dio.httpClientAdapter = adapter;
  return AwnApiClient(dio: dio);
}

void main() {
  test('listEvents parses published events', () async {
    final adapter = StubHttpClientAdapter()
      ..when('/events', <Object>[
        <String, Object?>{
          'id': 1,
          'eventCode': 'EVT-F1-SH',
          'name': 'F1 中国大奖赛',
          'status': 'PUBLISHED',
          'startTime': '2026-11-06T11:30:00Z',
          'endTime': '2026-11-06T13:30:00Z',
          'venueId': null,
        },
      ]);
    final events = await _clientWith(adapter).listEvents();
    expect(events, hasLength(1));
    expect(events.first.name, 'F1 中国大奖赛');
  });

  test('listEventSessions hits the session list endpoint', () async {
    final adapter = StubHttpClientAdapter()
      ..when('/events/1/sessions', <Object>[
        <String, Object?>{
          'id': 9,
          'sessionCode': 'SES-01',
          'eventId': 1,
          'name': '排位赛',
          'status': 'PUBLISHED',
          'startTime': '2026-11-06T11:30:00Z',
          'endTime': '2026-11-06T13:30:00Z',
        },
      ]);
    final sessions = await _clientWith(adapter).listEventSessions(1);
    expect(sessions.single.sessionCode, 'SES-01');
  });

  test('getSessionProducts parses tiers with pool code', () async {
    final adapter = StubHttpClientAdapter()
      ..when('/sessions/9/products', <Object>[
        <String, Object?>{
          'productCode': 'p-1',
          'name': '内场 A 区',
          'status': 'PUBLISHED',
          'unitPrice': 168000,
          'categoryId': null,
          'sessionId': 9,
          'poolCode': 'POOL-1',
          'available': 5,
        },
        <String, Object?>{
          'productCode': 'p-2',
          'name': '看台 B 区',
          'status': 'PUBLISHED',
          'unitPrice': 48000,
          'categoryId': null,
          'sessionId': 9,
          'poolCode': null,
          'available': null,
        },
      ]);
    final products = await _clientWith(adapter).getSessionProducts(9);
    expect(products, hasLength(2));
    expect(products.first.poolCode, 'POOL-1');
    expect(products.last.poolCode, isNull);
  });

  test('error envelope becomes ApiException with backend code', () async {
    final adapter = StubHttpClientAdapter()
      ..when(
        '/events/404',
        StubFailure(404, <String, Object?>{
          'code': 'EVENT_NOT_FOUND',
          'message': 'event not found: 404',
          'traceId': '3f9c2a',
        }),
      );
    final client = _clientWith(adapter);
    await expectLater(
      client.getEvent(404),
      throwsA(isA<ApiException>()
          .having((e) => e.code, 'code', 'EVENT_NOT_FOUND')
          .having((e) => e.message, 'message', 'event not found: 404')),
    );
  });

  test('non-envelope failure keeps the http status as the code', () async {
    final adapter = StubHttpClientAdapter()
      ..when(
        '/events/500',
        StubFailure(500, 'Internal Server Error'),
      );
    final client = _clientWith(adapter);
    await expectLater(
      client.getEvent(500),
      throwsA(isA<ApiException>()
          .having((e) => e.code, 'code', 'HTTP_500')),
    );
  });
}
