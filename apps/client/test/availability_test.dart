import 'package:flutter_test/flutter_test.dart';
import 'package:awn_client/src/availability.dart';

/// 可售状态派生口径（与 apps/web、apps/miniprogram 同一套规则）。
void main() {
  test('null availability means not on sale, not sold out', () {
    expect(deriveAvailability(null), Availability.notOnSale);
    expect(availabilityLabel(Availability.notOnSale), '未开售');
  });

  test('zero availability means sold out', () {
    expect(deriveAvailability(0), Availability.soldOut);
  });

  test('flags low stock at or below the threshold', () {
    expect(deriveAvailability(1), Availability.lowStock);
    expect(deriveAvailability(20), Availability.lowStock);
    expect(deriveAvailability(21), Availability.selling);
  });

  test('labels cover every enum value without inventing states', () {
    expect(Availability.values.length, 4);
    expect(availabilityLabel(Availability.selling), '在售');
    expect(availabilityLabel(Availability.lowStock), '余票紧张');
    expect(availabilityLabel(Availability.soldOut), '已售罄');
  });
}
