/// 展示格式化辅助（依赖零包，口径与 apps/web 的 formatEventDateTime 对齐）。
String formatEventTime(DateTime time) {
  final month = time.month.toString().padLeft(2, '0');
  final day = time.day.toString().padLeft(2, '0');
  final hour = time.hour.toString().padLeft(2, '0');
  final minute = time.minute.toString().padLeft(2, '0');
  return '$month月$day日 $hour:$minute';
}

/// 金额展示：分 → 「1,680.00」样式（千分位 + 两位小数，货币符号由调用方拼）。
String formatYuan(double yuan) {
  final whole = yuan.floor();
  final cents = ((yuan - whole) * 100).round();
  final digits = whole.toString();
  final buffer = StringBuffer();
  for (var i = 0; i < digits.length; i++) {
    final remaining = digits.length - i;
    buffer.write(digits[i]);
    if (remaining > 1 && (remaining - 1) % 3 == 0) {
      buffer.write(',');
    }
  }
  final centsText = cents.toString().padLeft(2, '0');
  return '$buffer.$centsText';
}
