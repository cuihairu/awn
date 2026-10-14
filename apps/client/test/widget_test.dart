import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:awn_client/main.dart';
import 'package:awn_client/src/api/awn_client.dart';

/// 只读链路走查：活动列表 → 活动页场次 → 票档列表（stub 传输层，不触网）。
void main() {
  late StubHttpClientAdapter adapter;

  AwnApiClient buildClient() {
    final dio = Dio(BaseOptions(
      baseUrl: 'http://stub/api/v1',
    ));
    dio.httpClientAdapter = adapter;
    return AwnApiClient(dio: dio);
  }

  setUp(() {
    adapter = StubHttpClientAdapter()
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
      ])
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
      ])
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
      ]);
  });

  testWidgets('renders the event list from the public api', (tester) async {
    await tester.pumpWidget(AwnClientApp(client: buildClient()));
    await tester.pumpAndSettle();

    expect(find.text('F1 中国大奖赛'), findsOneWidget);
    expect(find.text('在售'), findsOneWidget);
  });

  testWidgets('navigates to sessions then tiers read-only', (tester) async {
    await tester.pumpWidget(AwnClientApp(client: buildClient()));
    await tester.pumpAndSettle();

    await tester.tap(find.text('F1 中国大奖赛'));
    await tester.pumpAndSettle();
    expect(find.text('排位赛'), findsOneWidget);

    await tester.tap(find.text('排位赛'));
    await tester.pumpAndSettle();
    expect(find.text('内场 A 区'), findsOneWidget);
    expect(find.text('¥1,680.00'), findsOneWidget);
    // 余票紧张：available=5 ≤ 阈值 20（展示口径，与 web / 小程序一致）。
    expect(find.text('余票紧张'), findsOneWidget);
  });
}
