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

/**
 * 失败单是否可重新支付 / 可换渠道：只认后端下发的 retryable
 * （本单 FAILED 且所属订单仍待支付、未过支付截止期），前端不自行推断。
 */
export function canRetryPayment(payment: PaymentView | null): boolean {
  return payment?.status === 'FAILED' && payment.retryable === true
}

/** 支付成功只认渠道回调结果；前端参数、跳转参数都不作为成功依据 */
export function isPaymentSettled(payments: PaymentView[]): boolean {
  return payments.some((p) => p.status === 'SUCCEEDED')
}

/**
 * 支付倒计时剩余秒数：以 serverTime 为锚点推算，**不得用本机时钟判断是否超时**
 * （是否超时只认后端结论）。非待支付或已过期返回 0。
 */
export function payCountdownSeconds(order: OrderView | null, nowMs: number): number {
  if (!order || !canPay(order.status)) return 0
  const serverMs = Date.parse(order.serverTime)
  if (Number.isNaN(serverMs)) return 0
  const remaining = order.payExpiresInSeconds - (nowMs - serverMs) / 1000
  return Math.max(0, Math.ceil(remaining))
}

/** 倒计时展示：mm:ss，超 1 小时补小时位；0 展示 00:00（不再往下走负数） */
export function formatCountdown(seconds: number): string {
  if (seconds <= 0) return '00:00'
  const pad = (n: number) => String(n).padStart(2, '0')
  const h = Math.floor(seconds / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  const s = seconds % 60
  return h > 0 ? `${pad(h)}:${pad(m)}:${pad(s)}` : `${pad(m)}:${pad(s)}`
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
    seatNo: null,
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

/**
 * 提交前的客户端自检（数量型）；返回首条阻塞原因，无阻塞返回 null。
 * 空车提示是本页默认文案；同时有空座位车时由 validateCheckout 给出更准的提示。
 */
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

/** 座位购物车行：票档 + 已选座位号（座位 1:1，每座一行） */
export interface SeatCartLine {
  product: ProductListItem
  seatNos: string[]
}

/**
 * 座位购物车 → 下单行（座位型）：每座独立成行且 quantity=1，
 * 只给 seatNo，poolCode / listingNo 保持 null（服务端三路径互斥校验）。
 * 去重与空串过滤：座位图选区可重复点选，重复提交同一座位会被判冲突。
 */
export function toSeatOrderLines(seatCart: SeatCartLine[]): OrderLinePayload[] {
  const lines: OrderLinePayload[] = []
  for (const { product, seatNos } of seatCart) {
    const unique = [...new Set(seatNos)].filter((seatNo) => seatNo.trim() !== '')
    for (const seatNo of unique) {
      lines.push({
        poolCode: null,
        listingNo: null,
        seatNo,
        productId: product.productCode,
        productNameSnapshot: product.name,
        sessionId: product.sessionId ?? '',
        ticketCategorySnapshot: null,
        quantity: 1,
        unitPrice: product.unitPrice,
        discountAmount: 0,
        feeAmount: 0,
      })
    }
  }
  return lines
}

/** 座位车自检：座位型不需要库存池，只检查每个票档真的选了有效座位号 */
export function validateSeatCart(seatCart: SeatCartLine[]): string | null {
  for (const line of seatCart) {
    if (line.seatNos.length === 0) return `「${line.product.name}」未选择座位`
    if (line.seatNos.some((seatNo) => seatNo.trim() === ''))
      return `「${line.product.name}」存在无效座位号`
  }
  return null
}

/**
 * 数量车 + 座位车合并自检：座位型商品没有库存池，不能被数量型规则误判为「未开售」。
 * 两车都空时给出包含座位的提示（validateCart 只认识票档）。
 */
export function validateCheckout(cart: CartLine[], seatCart: SeatCartLine[]): string | null {
  if (cart.length === 0 && seatCart.length === 0) return '请先选择票档或座位'
  if (cart.length > 0) {
    const blocker = validateCart(cart)
    if (blocker) return blocker
  }
  if (seatCart.length > 0) {
    const blocker = validateSeatCart(seatCart)
    if (blocker) return blocker
  }
  return null
}

/** 座位车总座数：与数量车的 cartTotalQuantity 同口径（去重后计数） */
export function seatCartQuantity(seatCart: SeatCartLine[]): number {
  return seatCart.reduce(
    (sum, line) => sum + [...new Set(line.seatNos)].filter((n) => n.trim() !== '').length,
    0,
  )
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
export function formatOrderExpiry(iso: string | null | undefined): string {
  if (!iso) return ''
  const at = new Date(iso)
  if (Number.isNaN(at.getTime())) return ''
  // 手写格式化而不是 toLocaleString：输出与宿主 locale / ICU 版本无关
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${pad(at.getMonth() + 1)}-${pad(at.getDate())} ${pad(at.getHours())}:${pad(at.getMinutes())}`
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

/**
 * 渠道展示名映射（仅标签，不是渠道清单）：可下单渠道编码由后端
 * GET /payments/channels 下发，前端不硬编码渠道可用性。
 */
export function paymentChannelLabel(channel: string): string {
  return PAYMENT_CHANNELS.find((c) => c.key === channel)?.label ?? channel
}

/** 支付单列表按创建顺序展示，最新的在下（后端按创建顺序返回） */
export function newestPayment(payments: PaymentView[]): PaymentView | null {
  return payments.length > 0 ? payments[payments.length - 1] : null
}