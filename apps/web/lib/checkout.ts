import type {
  FulfillmentStatus,
  OrderItemView,
  OrderLinePayload,
  OrderStatus,
  OrderView,
  PaymentView,
  ProductListItem,
  QuoteView,
} from '@awn/api-client'

/**
 * 下单链路展示层逻辑（纯函数，无 React 依赖）。
 *
 * 后端语义对齐（docs/api/contracts.md + docs/architecture/trading-model.md）：
 * - 金额一律服务端重算，前端只展示报价结果（POST /checkout/quote 不扣库存）；
 * - 数量行必须带 poolCode，未建池商品拼不出合法订单行；
 * - 支付成功只来自渠道回调，前端不得据参数确认成功；结果未知（UNKNOWN）
 *   走对账，不能当失败展示（契约 §4.2 / payment-state-machine）。
 */

/** 购物车行：票档 + 数量 */
export interface CartLine {
  product: ProductListItem
  quantity: number
}

export const MAX_TICKETS_PER_LINE = 4

export const ORDER_STATUS_LABEL_ZH: Record<OrderStatus, string> = {
  PENDING_PAYMENT: '待支付',
  PAID: '已支付',
  FULFILLING: '履约中',
  FULFILLED: '已出票',
  REFUND_PENDING: '退款中',
  REFUNDED: '已退款',
  CANCELLED: '已取消',
  EXPIRED: '已超时关单',
}

export function orderStatusLabel(status: string): string {
  return ORDER_STATUS_LABEL_ZH[status as OrderStatus] ?? '未知状态'
}

/** 角标修饰类：可操作（待支付）与失败/终止态分色 */
const ORDER_STATUS_BADGE: Record<OrderStatus, string> = {
  PENDING_PAYMENT: 'badge--warning',
  PAID: 'badge--success',
  FULFILLING: 'badge--success',
  FULFILLED: 'badge--success',
  REFUND_PENDING: 'badge--warning',
  REFUNDED: '',
  CANCELLED: '',
  EXPIRED: '',
}

export function orderStatusBadge(status: string): string {
  return ORDER_STATUS_BADGE[status as OrderStatus] ?? ''
}

/** 终态订单不再轮询 */
export function isTerminalOrder(status: string): boolean {
  return (
    status === 'REFUNDED' || status === 'CANCELLED' || status === 'EXPIRED'
  )
}

/** 待支付才可发起支付与取消（订单状态机守卫，客户端先自筛一次） */
export function canPay(status: string): boolean {
  return status === 'PENDING_PAYMENT'
}

export const PAYMENT_STATUS_LABEL_ZH: Record<string, string> = {
  CREATED: '待支付',
  PENDING: '支付中',
  SUCCEEDED: '支付成功',
  FAILED: '支付失败',
  // 结果未知 ≠ 失败：等渠道回调或对账，前端不重试也不改判
  UNKNOWN: '结果待核实（对账中）',
  REFUNDING: '退款中',
  PARTIALLY_REFUNDED: '部分退款',
  REFUNDED: '已退款',
}

export function paymentStatusLabel(status: string): string {
  return PAYMENT_STATUS_LABEL_ZH[status] ?? '未知状态'
}

/** 结果未定的支付单不允许再次发起支付（避免重复扣款） */
export function paymentInFlight(status: string): boolean {
  return status === 'CREATED' || status === 'PENDING' || status === 'UNKNOWN'
}

/** 支付成功只认渠道回调结果；前端参数、跳转参数都不作为成功依据 */
export function isPaymentSettled(payments: PaymentView[]): boolean {
  return payments.some((p) => p.status === 'SUCCEEDED')
}

export const FULFILLMENT_STATUS_LABEL_ZH: Record<FulfillmentStatus, string> = {
  NOT_STARTED: '未开始',
  PENDING_SELLER: '待卖家交付',
  PENDING_VERIFICATION: '待平台核验',
  IN_PROGRESS: '出票中',
  COMPLETED: '已出票',
  FAILED: '履约失败',
  CANCELLED: '履约取消',
}

export function fulfillmentStatusLabel(status: string): string {
  return FULFILLMENT_STATUS_LABEL_ZH[status as FulfillmentStatus] ?? '未知状态'
}

/* ------------------------------------------------------------------ *
 * 购物车
 * ------------------------------------------------------------------ */

/** 该票档当前最多可买数量：展示口径（available），下单时后端原子重校验 */
export function maxQuantityFor(product: ProductListItem): number {
  if (product.available == null) return 0
  return Math.min(MAX_TICKETS_PER_LINE, product.available)
}

/** 选中票档进入购物车（未开售 / 已售罄不进购物车） */
export function addToCart(
  cart: CartLine[],
  product: ProductListItem,
  quantity = 1,
): CartLine[] {
  const cap = maxQuantityFor(product)
  if (cap <= 0) return cart
  const existing = cart.find((l) => l.product.productCode === product.productCode)
  if (!existing) return [...cart, { product, quantity: Math.min(quantity, cap) }]
  return setQuantity(cart, product.productCode, existing.quantity + quantity)
}

/** 改数量：夹在 [0, 上限]，0 视为移除该行 */
export function setQuantity(
  cart: CartLine[],
  productCode: string,
  quantity: number,
): CartLine[] {
  return cart
    .map((line) => {
      if (line.product.productCode !== productCode) return line
      const cap = maxQuantityFor(line.product)
      const next = Math.max(0, Math.min(quantity, cap))
      return { ...line, quantity: next }
    })
    .filter((line) => line.quantity > 0)
}

export function cartTotalQuantity(cart: CartLine[]): number {
  return cart.reduce((sum, line) => sum + line.quantity, 0)
}

/**
 * 购物车 → 下单行（数量型）：poolCode 必填，客户端只传单价与优惠，
 * 不传总价（服务端按同一服务端口径重算）。
 */
export function toOrderLines(cart: CartLine[]): OrderLinePayload[] {
  return cart.map((line) => ({
    poolCode: line.product.poolCode ?? null,
    listingNo: null,
    productId: line.product.productCode,
    productNameSnapshot: line.product.name,
    sessionId: line.product.sessionId ?? '',
    ticketCategorySnapshot: null,
    quantity: line.quantity,
    unitPrice: line.product.unitPrice,
    discountAmount: 0,
    feeAmount: 0,
  }))
}

/** 提交前的客户端自检；返回首条阻塞原因，无阻塞返回 null */
export function validateCart(cart: CartLine[]): string | null {
  if (cart.length === 0) return '请先选择票档'
  for (const line of cart) {
    if (!line.product.poolCode) return `「${line.product.name}」未开售（无库存池）`
    if ((line.product.available ?? 0) <= 0) return `「${line.product.name}」已售罄`
    if (line.quantity > maxQuantityFor(line.product))
      return `「${line.product.name}」超出可购数量`
  }
  return null
}

/**
 * 金额展示（最小货币单位整数 → 元）。报价与订单同源字段，
 * 报价为空时展示占位而不是伪造 0。
 */
export function formatMinor(minor: number | null | undefined): string {
  if (minor == null) return '—'
  return (minor / 100).toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })
}

/** 支付截止等时间戳展示；缺失或不可解析时返回空串（不展示「Invalid Date」） */
export function formatInstant(iso: string | null | undefined): string {
  if (!iso) return ''
  const at = new Date(iso)
  if (Number.isNaN(at.getTime())) return ''
  return at.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  })
}

/** 报价区行：票档 × 数量与服务端算出的行合计 */
export interface QuoteRow {
  key: string
  name: string
  quantity: number
  lineTotal: string
}

export function quoteRows(order: OrderView): QuoteRow[] {
  return order.items.map((item: OrderItemView) => ({
    key: item.productId,
    name: item.productNameSnapshot,
    quantity: item.quantity,
    lineTotal: formatMinor(item.lineTotal),
  }))
}

export function quoteTotals(quote: QuoteView | null): {
  total: string
  discount: string
  fee: string
  pay: string
} {
  return {
    total: formatMinor(quote?.totalAmount),
    discount: formatMinor(quote?.discountAmount),
    fee: formatMinor(quote?.feeAmount),
    pay: formatMinor(quote?.payAmount),
  }
}

/** 下单幂等键：同一次结算尝试内保持不变，改购物车内容后换新键 */
export function newIdempotencyKey(): string {
  const cryptoRef = globalThis.crypto
  if (cryptoRef && typeof cryptoRef.randomUUID === 'function') {
    return cryptoRef.randomUUID()
  }
  return `awn-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`
}

/** 渠道选项：沙箱唯一可用渠道（真实渠道需商户配置） */
export const PAYMENT_CHANNELS: { key: string; label: string; note: string }[] = [
  { key: 'sandbox', label: '沙箱支付', note: '渠道回调确认成功，网页端不凭参数确认支付' },
]

export function paymentChannelLabel(channel: string): string {
  return PAYMENT_CHANNELS.find((c) => c.key === channel)?.label ?? channel
}

/** 支付单列表按创建顺序展示，最新的在下（后端按创建顺序返回） */
export function newestPayment(payments: PaymentView[]): PaymentView | null {
  return payments.length > 0 ? payments[payments.length - 1] : null
}