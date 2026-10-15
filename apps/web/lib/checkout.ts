import type {
  AttendeeView,
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

/** 演示身份 localStorage 键：结算页写入，订单页（争议等需要 userId 的操作）读取 */
export const DEMO_USER_KEY = 'awn-demo-user'

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
 * 观演人实名（一票一证，docs/api/contracts.md「观演人实名（一票一证）」）
 * ------------------------------------------------------------------ */

/** 观演人表单行（与后端 AttendeeCommand 同字段名） */
export interface AttendeeInput {
  name: string
  idNumber: string
}

/** 表单行表：键为票档编码（数量行）或「票档编码:座位号」（座位行） */
export type AttendeeRecord = Record<string, AttendeeInput[]>

/** 后端字段上限（AttendeeCommand：姓名 ≤64、证件号 ≤32），前端先挡一次 */
export const ATTENDEE_NAME_MAX = 64
export const ATTENDEE_ID_NO_MAX = 32

const EMPTY_ATTENDEE: AttendeeInput = { name: '', idNumber: '' }

/** 表单行键：数量行按票档，座位行按「票档:座位号」（同票档多座互不覆盖） */
export function attendeeRowKey(productCode: string, seatNo?: string): string {
  return seatNo ? `${productCode}:${seatNo}` : productCode
}

/** 该行应渲染的观演人行：补齐到票数并保留已填内容（不改原记录） */
export function attendeeRowsFor(
  record: AttendeeRecord,
  key: string,
  count: number,
): AttendeeInput[] {
  const rows = record[key] ?? []
  return Array.from({ length: Math.max(0, count) }, (_, i) => rows[i] ?? EMPTY_ATTENDEE)
}

/** 改某一行的某个字段（不可变更新） */
export function setAttendeeRow(
  record: AttendeeRecord,
  key: string,
  index: number,
  patch: Partial<AttendeeInput>,
): AttendeeRecord {
  const rows = attendeeRowsFor(record, key, index + 1).map((row, i) =>
    i === index ? { ...row, ...patch } : row,
  )
  return { ...record, [key]: rows }
}

const isBlank = (value: string | undefined) => !value || value.trim() === ''

const rowEmpty = (row: AttendeeInput) => isBlank(row.name) && isBlank(row.idNumber)

/** 已填行集合的逐行校验：缺字段 / 超长各给首条原因（行号从 1 起，与页面标签一致） */
function incompleteReason(rows: AttendeeInput[], label: string): string | null {
  if (rows.every(rowEmpty)) return null
  for (let i = 0; i < rows.length; i += 1) {
    const row = rows[i]
    if (isBlank(row.name)) return `${label}观演人 ${i + 1} 请填写姓名`
    if (isBlank(row.idNumber)) return `${label}观演人 ${i + 1} 请填写证件号`
    if (row.name.trim().length > ATTENDEE_NAME_MAX)
      return `${label}观演人 ${i + 1} 姓名过长（最多 ${ATTENDEE_NAME_MAX} 字）`
    if (row.idNumber.trim().length > ATTENDEE_ID_NO_MAX)
      return `${label}观演人 ${i + 1} 证件号过长（最多 ${ATTENDEE_ID_NO_MAX} 位）`
  }
  return null
}

/**
 * 观演人表单自检；返回首条阻塞原因，无阻塞返回 null。
 * 与后端同口径：实名可选（不填则整单不带 attendees）、一票一证（行数=票数）、
 * 同场次同证件限 1 张（无场次的行不参与判重）、字段长度上限。
 * 重复证件最终由后端 409 裁决，这里只做同口径先挡。
 */
export function validateAttendees(
  record: AttendeeRecord,
  cart: CartLine[],
  seatCart: SeatCartLine[],
): string | null {
  const seen = new Set<string>()
  const lines = [
    ...cart.map((line) => ({
      key: attendeeRowKey(line.product.productCode),
      count: line.quantity,
      label: `「${line.product.name}」`,
      sessionId: line.product.sessionId ?? '',
    })),
    ...seatCart.flatMap((line) =>
      [...new Set(line.seatNos)]
        .filter((seatNo) => seatNo.trim() !== '')
        .map((seatNo) => ({
          key: attendeeRowKey(line.product.productCode, seatNo),
          count: 1,
          label: `「${line.product.name} ${seatNo}」`,
          sessionId: line.product.sessionId ?? '',
        })),
    ),
  ]
  for (const line of lines) {
    const rows = attendeeRowsFor(record, line.key, line.count)
    const incomplete = incompleteReason(rows, line.label)
    if (incomplete) return incomplete
    // 无场次（sessionId 空）与后端同口径：不参与同场次判重
    if (isBlank(line.sessionId)) continue
    for (const row of rows) {
      if (rowEmpty(row)) continue
      const fingerprint = `${line.sessionId}|${row.idNumber.trim()}`
      if (seen.has(fingerprint)) return '同一观演人证件号在本单重复：本场次同一证件限 1 张'
      seen.add(fingerprint)
    }
  }
  return null
}

/**
 * 该行的实名载荷：整行未填 → null（实名可选，后端把空数组判为数量不符 400）；
 * 填了一半 → 同样 null（页面已被 validateAttendees 阻塞，不补发非法载荷）。
 */
function attendeePayload(
  record: AttendeeRecord,
  key: string,
  count: number,
): AttendeeInput[] | null {
  const rows = attendeeRowsFor(record, key, count)
  if (rows.every(rowEmpty) || incompleteReason(rows, '') !== null) return null
  return rows.map((row) => ({ name: row.name.trim(), idNumber: row.idNumber.trim() }))
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
 * 不传总价（服务端按同一服务端口径重算）；实名行按票档键挂 attendees（一票一证）。
 */
export function toOrderLines(cart: CartLine[], attendees: AttendeeRecord = {}): OrderLinePayload[] {
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
    attendees: attendeePayload(attendees, attendeeRowKey(line.product.productCode), line.quantity),
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
 * 去重与空串过滤：座位图选区可重复点选，重复提交同一座位会被判冲突；
 * 实名按「票档:座位号」键逐座挂 attendees。
 */
export function toSeatOrderLines(
  seatCart: SeatCartLine[],
  attendees: AttendeeRecord = {},
): OrderLinePayload[] {
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
        attendees: attendeePayload(attendees, attendeeRowKey(product.productCode, seatNo), 1),
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

/**
 * 订单页实名展示：证件号用服务端下发的脱敏值原样呈现，
 * 前端不再加工（本地二次脱敏只会让展示与后端口径漂移）。
 */
export function attendeesLabel(attendees: AttendeeView[] | null | undefined): string {
  if (!attendees || attendees.length === 0) return ''
  return attendees.map((a) => `${a.name} ${a.idNumber}`).join('、')
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