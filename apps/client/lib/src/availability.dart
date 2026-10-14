/// 可售状态派生（web / 小程序 / 客户端统一口径）：
///
/// null → 未开售（未建库存池，不等于已售罄）、0 → 已售罄、
/// ≤ 阈值(20) → 余票紧张、其余 → 在售。展示口径不承担下单校验，
/// 下单时后端原子预留重校验（docs/api/contracts.md）。
enum Availability {
  notOnSale,
  soldOut,
  lowStock,
  selling,
}

/// 余票紧张阈值，与 apps/web、apps/miniprogram 的 LOW_STOCK_THRESHOLD 一致。
const int lowStockThreshold = 20;

Availability deriveAvailability(int? available) {
  if (available == null) {
    return Availability.notOnSale;
  }
  if (available <= 0) {
    return Availability.soldOut;
  }
  if (available <= lowStockThreshold) {
    return Availability.lowStock;
  }
  return Availability.selling;
}

/// 状态中文文案，不发明后端没有的状态。
String availabilityLabel(Availability availability) {
  switch (availability) {
    case Availability.notOnSale:
      return '未开售';
    case Availability.soldOut:
      return '已售罄';
    case Availability.lowStock:
      return '余票紧张';
    case Availability.selling:
      return '在售';
  }
}
