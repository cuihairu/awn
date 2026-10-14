import type { OrderLinePayload, ProductListItem } from './mp-api'

/**
 * 下单链路纯逻辑（无 React、无请求依赖）。
 *
 * 口径：
 * - 票档可售状态由 available 派生（null 未开售 / 0 售罄 / ≤20 余票紧张），
 *   与 web 端同一口径；下单时后端原子预留重校验，展示值不构成承诺；
 * - 数量行必须带 poolCode（公开商品响应已透出），未建池的票档不可下单；
 * - 金额由服务端重算，客户端只算展示合计（最小货币单位整数）。
 */

export const LOW_STOCK_THRESHOLD = 20

export type AvailabilityLabel = 'Selling' | 'LowStock' | 'SoldOut' | 'NotOnSale'

export function deriveAvailability(available: number | null): AvailabilityLabel {
  if (available == null) return 'NotOnSale'
  if (available <= 0) return 'SoldOut'
  if (available <= LOW_STOCK_THRESHOLD) return 'LowStock'
  return 'Selling'
}

export const AVAILABILITY_LABEL_ZH: Record<AvailabilityLabel, string> = {
  Selling: '售票中',
  LowStock: '余票紧张',
  SoldOut: '已售罄',
  NotOnSale: '未开售',
}

export interface CartLine {
  product: ProductListItem
  quantity: number
}

export interface OrderDraft {
  lines: OrderLinePayload[]
  /** 展示合计（分），服务端重算为准 */
  totalMinor: number
}

/** 票档 → 下单行（poolCode 缺失的行不可下单，由 validateDraft 拦截） */
export function toOrderLine(product: ProductListItem, quantity: number): OrderLinePayload {
  return {
    poolCode: product.poolCode ?? '',
    productId: product.productCode,
    productNameSnapshot: product.name,
    sessionId: product.sessionId ?? '',
    quantity,
    unitPrice: product.unitPrice,
    discountAmount: 0,
    feeAmount: 0,
  }
}

export function buildDraft(lines: CartLine[]): OrderDraft {
  return {
    lines: lines.map((line) => toOrderLine(line.product, line.quantity)),
    totalMinor: lines.reduce((sum, line) => sum + line.product.unitPrice * line.quantity, 0),
  }
}

export interface DraftIssue {
  productCode: string
  message: string
}

/** 下单前校验：数量正整数、票档可售、池编码齐备 */
export function validateDraft(lines: CartLine[]): DraftIssue[] {
  const issues: DraftIssue[] = []
  for (const line of lines) {
    if (!Number.isInteger(line.quantity) || line.quantity <= 0) {
      issues.push({ productCode: line.product.productCode, message: '数量必须为正整数' })
      continue
    }
    if (line.product.poolCode == null) {
      issues.push({ productCode: line.product.productCode, message: '该票档未开售（无库存池）' })
      continue
    }
    if (deriveAvailability(line.product.available) === 'SoldOut') {
      issues.push({ productCode: line.product.productCode, message: '该票档已售罄' })
    }
  }
  return issues
}

/** 幂等键：每次进入确认页生成一次，重试复用同键（同键同请求返回原订单） */
export function newIdempotencyKey(): string {
  const cryptoObj = typeof crypto !== 'undefined' ? crypto : null
  if (cryptoObj?.randomUUID) return cryptoObj.randomUUID()
  return `mp-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`
}

/** 最小货币单位整数 → 「3,880.00」 */
export function minorToYuan(minor: number): string {
  if (!Number.isFinite(minor)) return ''
  return (minor / 100).toLocaleString('en-US', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })
}

/** ISO → 「11-02 19:30」（本地时区），非法时间返回空串 */
export function formatMpTime(iso: string | null): string {
  if (!iso) return ''
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return ''
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}
