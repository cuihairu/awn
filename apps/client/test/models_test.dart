import 'package:flutter_test/flutter_test.dart';
import 'package:awn_client/src/format.dart';
import 'package:awn_client/src/models/event.dart';
import 'package:awn_client/src/models/product.dart';
import 'package:awn_client/src/models/session.dart';

/// 模型与后端记录（site.awn.event.api.EventView / SessionView、
/// site.awn.api.ProductListItem）的字段一一对应；Long 字段按 JSON 数字解析。
void main() {
  test('parses event view', () {
    final event = EventView.fromJson(<String, dynamic>{
      'id': 1,
      'eventCode': 'EVT-F1-SH',
      'name': 'F1 中国大奖赛',
      'status': 'PUBLISHED',
      'startTime': '2026-11-06T11:30:00Z',
      'endTime': '2026-11-06T13:30:00Z',
      'venueId': null,
    });
    expect(event.id, 1);
    expect(event.eventCode, 'EVT-F1-SH');
    expect(event.isPublished, isTrue);
    expect(event.startTime.toUtc().toIso8601String(),
        '2026-11-06T11:30:00.000Z');
    expect(event.venueId, isNull);
  });

  test('parses session view', () {
    final session = SessionView.fromJson(<String, dynamic>{
      'id': 9,
      'sessionCode': 'SES-01',
      'eventId': 1,
      'name': '排位赛',
      'status': 'PUBLISHED',
      'startTime': '2026-11-06T11:30:00Z',
      'endTime': '2026-11-06T13:30:00Z',
    });
    expect(session.id, 9);
    expect(session.eventId, 1);
    expect(session.name, '排位赛');
  });

  test('parses product list item and derives yuan price', () {
    final product = ProductListItem.fromJson(<String, dynamic>{
      'productCode': 'p-1',
      'name': '内场 A 区',
      'status': 'PUBLISHED',
      'unitPrice': 168000,
      'categoryId': null,
      'sessionId': 9,
      'poolCode': 'POOL-1',
      'available': 120,
    });
    expect(product.unitPrice, 168000);
    expect(product.unitPriceYuan, 1680.0);
    expect(product.poolCode, 'POOL-1');
    expect(product.available, 120);
  });

  test('poolless product carries null pool and null availability', () {
    final product = ProductListItem.fromJson(<String, dynamic>{
      'productCode': 'p-2',
      'name': '看台 B 区',
      'status': 'PUBLISHED',
      'unitPrice': 48000,
      'categoryId': null,
      'sessionId': 9,
      'poolCode': null,
      'available': null,
    });
    expect(product.poolCode, isNull);
    expect(product.available, isNull);
  });

  test('formats time and yuan for display', () {
    expect(formatEventTime(DateTime(2026, 11, 6, 19, 30)), '11月06日 19:30');
    expect(formatYuan(1680), '1,680.00');
    expect(formatYuan(48.5), '48.50');
    expect(formatYuan(12800), '12,800.00');
  });
}
