import type { EventStatus, ProductListItem } from '@awn/api-client'
import type { AvailabilityLabel, TicketTier } from '@awn/ticket-components'

/**
 * 活动 / 票档展示层文案与映射（纯函数，无 React 依赖）。
 *
 * 后端只存编码与最小货币单位整数；中文文案与元展示在前端维护。
 * 可售状态从 available 派生（docs/api/contracts.md：数量为缓存展示口径，
 * 下单时后端仍原子重校验），不发明后端没有的状态。
 */

/** 余票紧张阈值：available ≤ 该值时展示「余票紧张」 */
export const LOW_STOCK_THRESHOLD = 20

export const EVENT_STATUS_LABEL_ZH: Record<EventStatus, string> = {
  DRAFT: '草稿',
  PUBLISHED: '售票中',
  CANCELLED: '已取消',
  COMPLETED: '已结束',
}

export function eventStatusLabel(status: string): string {
  return EVENT_STATUS_LABEL_ZH[status as EventStatus] ?? '未知状态'
}

/** ISO 时间 → 「11 月 6 日 19:30」（本地时区展示） */
export function formatEventDateTime(iso: string): string {
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return ''
  const month = date.getMonth() + 1
  const day = date.getDate()
  const time = `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
  return `${month} 月 ${day} 日 ${time}`
}

/**
 * 库存展示口径 → 可售状态标签：
 * null（未建池）= 未开售；0 = 已售罄；≤ 阈值 = 余票紧张；其余 = 售票中。
 */
export function deriveAvailability(
  available: number | null,
  threshold: number = LOW_STOCK_THRESHOLD,
): AvailabilityLabel {
  if (available == null) return 'NotOnSale'
  if (available <= 0) return 'SoldOut'
  if (available <= threshold) return 'LowStock'
  return 'Selling'
}

/** ProductListItem（后端公开商品）→ TicketTier（票档卡契约） */
export function productToTier(product: ProductListItem): TicketTier {
  return {
    productId: product.productCode,
    name: product.name,
    sessionId: product.sessionId ?? '',
    poolCode: product.poolCode ?? null,
    unitPriceMinor: product.unitPrice,
    currency: 'CNY',
    // 后端购买规则（限购）未下发前不渲染限购行
    quantityLimit: null,
    availability: deriveAvailability(product.available),
  }
}
