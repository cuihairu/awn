/**
 * AWN API 客户端。
 *
 * 契约来源：docs/api/contracts.md（/api/v1）。
 * 订单创建必须携带 Idempotency-Key；相同幂等键 + 相同请求返回原结果，
 * 相同幂等键 + 不同请求返回冲突错误。
 */

export const API_BASE = '/api/v1'

/**
 * 统一响应处理：非 2xx 抛 ApiError（携带 HTTP 状态与后端错误码）。
 * 调用方据此区分「资源不存在」（404）与「链路 / 服务故障」（其余）——
 * 不再把错误信封当成功体返回，避免列表页把 {code,message} 当数组渲染。
 */
export class ApiError extends Error {
  readonly status: number
  readonly code: string

  constructor(status: number, code: string, message: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
  }
}

async function readJson<T>(res: Response): Promise<T> {
  const body: unknown = await res.json().catch(() => null)
  if (!res.ok) {
    const envelope = body as { code?: unknown; message?: unknown } | null
    const code = typeof envelope?.code === 'string' ? envelope.code : `HTTP_${res.status}`
    const message =
      typeof envelope?.message === 'string' ? envelope.message : res.statusText
    throw new ApiError(res.status, code, message)
  }
  return body as T
}

/**
 * 交易（docs/api/contracts.md 交易接口）：
 * 订单创建必须带 Idempotency-Key；金额一律服务端重算，客户端只传单价与优惠；
 * 支付创建只传渠道，金额/币种取订单快照（任何端不得凭前端参数确认支付成功）。
 */

/** 订单行来源二选一：数量型走 poolCode，凭证型走 listingNo */
export interface OrderLinePayload {
  poolCode: string | null
  listingNo: string | null
  productId: string
  productNameSnapshot: string
  sessionId: string
  ticketCategorySnapshot: string | null
  quantity: number
  unitPrice: number
  discountAmount: number
  feeAmount: number
}

export interface OrderCreatePayload {
  /** 客户端生成的幂等键（放 Idempotency-Key 头，不进请求体） */
  idempotencyKey: string
  userId: string
  currency: string
  lines: OrderLinePayload[]
}

export type OrderStatus =
  | 'PENDING_PAYMENT'
  | 'PAID'
  | 'FULFILLING'
  | 'FULFILLED'
  | 'REFUND_PENDING'
  | 'REFUNDED'
  | 'CANCELLED'
  | 'EXPIRED'

export interface OrderItemView {
  poolCode: string | null
  productId: string
  productNameSnapshot: string
  sessionId: string
  ticketCategorySnapshot: string | null
  quantity: number
  unitPrice: number
  discountAmount: number
  feeAmount: number
  lineTotal: number
  reservationNo: string | null
  listingNo: string | null
  unitNo: string | null
}

export interface OrderView {
  orderNo: string
  status: OrderStatus
  currency: string
  totalAmount: number
  discountAmount: number
  feeAmount: number
  payAmount: number
  expiresAt: string | null
  items: OrderItemView[]
}

export interface QuoteView {
  currency: string
  totalAmount: number
  discountAmount: number
  feeAmount: number
  payAmount: number
}

export interface PaymentView {
  paymentNo: string
  orderNo: string
  channel: string
  channelTxnNo: string | null
  status: string
  currency: string
  amount: number
  paidAt: string | null
}

export async function createOrder(
  payload: OrderCreatePayload,
  init?: RequestInit,
): Promise<OrderView> {
  const { idempotencyKey, ...body } = payload
  return fetch(`${API_BASE}/orders`, {
    ...init,
    method: 'POST',
    headers: {
      'content-type': 'application/json',
      'idempotency-key': idempotencyKey,
      ...(init?.headers ?? {}),
    },
    body: JSON.stringify(body),
  }).then((res) => readJson<OrderView>(res))
}

/** 结算报价：不扣库存、不产生订单；下单时服务端重新计算并原子预留 */
export async function quoteCheckout(
  payload: { currency: string; lines: OrderLinePayload[] },
  init?: RequestInit,
): Promise<QuoteView> {
  return fetch(`${API_BASE}/checkout/quote`, {
    ...init,
    method: 'POST',
    headers: {
      'content-type': 'application/json',
      ...(init?.headers ?? {}),
    },
    body: JSON.stringify(payload),
  }).then((res) => readJson<QuoteView>(res))
}

export async function getOrder(
  orderNo: string,
  init?: RequestInit,
): Promise<OrderView> {
  return fetch(`${API_BASE}/orders/${orderNo}`, init)
    .then((res) => readJson<OrderView>(res))
}

export async function cancelOrder(
  orderNo: string,
  init?: RequestInit,
): Promise<OrderView> {
  return fetch(`${API_BASE}/orders/${orderNo}/cancel`, {
    ...init,
    method: 'POST',
  }).then((res) => readJson<OrderView>(res))
}

/** 发起支付：只传渠道，金额/币种服务端取订单快照 */
export async function createPayment(
  orderNo: string,
  channel: string,
  init?: RequestInit,
): Promise<PaymentView> {
  return fetch(`${API_BASE}/orders/${orderNo}/payments`, {
    ...init,
    method: 'POST',
    headers: {
      'content-type': 'application/json',
      ...(init?.headers ?? {}),
    },
    body: JSON.stringify({ channel }),
  }).then((res) => readJson<PaymentView>(res))
}

export async function listPayments(
  orderNo: string,
  init?: RequestInit,
): Promise<PaymentView[]> {
  return fetch(`${API_BASE}/orders/${orderNo}/payments`, init).then((res) =>
    readJson<PaymentView[]>(res),
  )
}

/** 履约状态（独立于订单与支付状态机：支付成功只推进到 PENDING_SELLER） */
export type FulfillmentStatus =
  | 'NOT_STARTED'
  | 'PENDING_SELLER'
  | 'PENDING_VERIFICATION'
  | 'IN_PROGRESS'
  | 'COMPLETED'
  | 'FAILED'
  | 'CANCELLED'

export interface FulfillmentView {
  fulfillmentNo: string
  orderNo: string
  status: FulfillmentStatus
  failReason: string | null
}

/** 履约单查询；订单未支付进入履约或未知 → 404（由调用方区分文案） */
export async function getFulfillment(
  orderNo: string,
  init?: RequestInit,
): Promise<FulfillmentView> {
  return fetch(`${API_BASE}/orders/${orderNo}/fulfillment`, init).then((res) =>
    readJson<FulfillmentView>(res),
  )
}

/* ------------------------------------------------------------------ *
 * 明星图鉴（docs/design/star-catalog.md）
 * 公开读只返回 PUBLISHED；photoUrl 为 null 表示无授权图片，按占位图渲染。
 * 人物与作品是多对多，关系行（PersonWorkEntry）带 roleType / roleName /
 * billingOrder——两端详情都复用同一条关系结构，互相跳转时不必再查一次。
 * ------------------------------------------------------------------ */

export type ProfessionType =
  | 'actor'
  | 'singer'
  | 'host'
  | 'director'
  | 'musician'
  | 'producer'
  | 'screenwriter'
  | 'dancer'
  | 'model'

export type WorkType =
  | 'movie'
  | 'tv'
  | 'variety'
  | 'song'
  | 'album'
  | 'concert'
  | 'stage'

export type RoleType =
  | 'cast_lead'
  | 'cast_supporting'
  | 'cast_cameo'
  | 'director'
  | 'screenwriter'
  | 'producer'
  | 'composer'
  | 'lyricist'
  | 'theme_song'
  | 'voice'

export interface PersonView {
  id: string
  name: string
  originalName: string | null
  aliases: string[]
  nationality: string
  professionTypes: ProfessionType[]
  bio: string | null
  /** 仅在有授权来源时透出；null 表示未收录图片，前端渲染占位图 */
  photoUrl: string | null
  status: string
}

export interface WorkView {
  id: string
  title: string
  type: WorkType
  year: number | null
  description: string | null
  status: string
}

/** 关系行：一人 + 一部作品 + 一种身份 */
export interface PersonWorkEntry {
  personId: string
  workId: string
  personName: string
  workTitle: string
  workType: WorkType
  workYear: number | null
  roleType: RoleType
  roleName: string
  billingOrder: number | null
}

export interface PersonDetail {
  person: PersonView
  works: PersonWorkEntry[]
}

export interface WorkDetail {
  work: WorkView
  cast: PersonWorkEntry[]
}

export interface PersonQuery {
  profession?: string
  nationality?: string
  q?: string
}

export interface WorkQuery {
  type?: string
  q?: string
}

/** 空值参数不下发，避免 ?q= 之类的空查询条件落到后端 */
function withQuery(path: string, params: Record<string, string | undefined>): string {
  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    const trimmed = value?.trim()
    if (trimmed) search.set(key, trimmed)
  }
  const qs = search.toString()
  return qs ? `${API_BASE}${path}?${qs}` : `${API_BASE}${path}`
}

export async function listPersons(
  query: PersonQuery = {},
  init?: RequestInit,
): Promise<PersonView[]> {
  return fetch(
    withQuery('/stars', {
      profession: query.profession,
      nationality: query.nationality,
      q: query.q,
    }),
    init,
  ).then((res) => readJson<PersonView[]>(res))
}

export async function getPerson(
  personId: string,
  init?: RequestInit,
): Promise<PersonDetail> {
  return fetch(`${API_BASE}/stars/${personId}`, init)
    .then((res) => readJson<PersonDetail>(res))
}

export async function listWorks(
  query: WorkQuery = {},
  init?: RequestInit,
): Promise<WorkView[]> {
  return fetch(
    withQuery('/works', { type: query.type, q: query.q }),
    init,
  ).then((res) => readJson<WorkView[]>(res))
}

export async function getWork(
  workId: string,
  init?: RequestInit,
): Promise<WorkDetail> {
  return fetch(`${API_BASE}/works/${workId}`, init)
    .then((res) => readJson<WorkDetail>(res))
}

/* ------------------------------------------------------------------ *
 * 活动 / 场次 / 商品（docs/api/contracts.md 公开接口）
 * 公开面只暴露已发布内容，未发布与不存在一律 404；
 * 商品可售数量为缓存展示口径，下单时后端仍原子预留重校验。
 * ------------------------------------------------------------------ */

export type EventStatus = 'DRAFT' | 'PUBLISHED' | 'CANCELLED' | 'COMPLETED'
export type SessionStatus = 'DRAFT' | 'PUBLISHED' | 'CANCELLED'
export type ProductStatus = 'DRAFT' | 'PUBLISHED' | 'CANCELLED'

export interface EventView {
  id: string
  eventCode: string
  name: string
  status: EventStatus
  startTime: string
  endTime: string
  venueId: string | null
}

export interface SessionView {
  id: string
  sessionCode: string
  eventId: string
  name: string
  status: SessionStatus
  startTime: string
  endTime: string
}

export interface ProductResponse {
  productCode: string
  name: string
  status: ProductStatus
  unitPrice: number
  categoryId: string | null
  sessionId: string | null
  /** 数量行下单所需（POST /orders 的 lines[].poolCode）；未建池为 null */
  poolCode: string | null
}

export interface AvailabilityResponse {
  productId: string
  status: ProductStatus
  sellable: boolean
  poolCode: string | null
  total: number | null
  available: number | null
}

export interface ProductListItem {
  productCode: string
  name: string
  status: ProductStatus
  unitPrice: number
  categoryId: string | null
  sessionId: string | null
  /** 数量行下单所需（POST /orders 的 lines[].poolCode）；未建池为 null */
  poolCode: string | null
  available: number | null
}

export async function listEvents(init?: RequestInit): Promise<EventView[]> {
  return fetch(`${API_BASE}/events`, init).then((res) => readJson<EventView[]>(res))
}

export async function getEvent(
  eventId: string,
  init?: RequestInit,
): Promise<EventView> {
  return fetch(`${API_BASE}/events/${eventId}`, init)
    .then((res) => readJson<EventView>(res))
}

export async function listEventSessions(
  eventId: string,
  init?: RequestInit,
): Promise<SessionView[]> {
  return fetch(`${API_BASE}/events/${eventId}/sessions`, init)
    .then((res) => readJson<SessionView[]>(res))
}

export async function getSession(
  sessionId: string,
  init?: RequestInit,
): Promise<SessionView> {
  return fetch(`${API_BASE}/sessions/${sessionId}`, init)
    .then((res) => readJson<SessionView>(res))
}

export async function getSessionProducts(
  sessionId: string,
  init?: RequestInit,
): Promise<ProductListItem[]> {
  return fetch(`${API_BASE}/sessions/${sessionId}/products`, init)
    .then((res) => readJson<ProductListItem[]>(res))
}

export async function getProduct(
  productId: string,
  init?: RequestInit,
): Promise<ProductResponse> {
  return fetch(`${API_BASE}/products/${productId}`, init)
    .then((res) => readJson<ProductResponse>(res))
}

export async function getProductAvailability(
  productId: string,
  init?: RequestInit,
): Promise<AvailabilityResponse> {
  return fetch(`${API_BASE}/products/${productId}/availability`, init)
    .then((res) => readJson<AvailabilityResponse>(res))
}

/* ------------------------------------------------------------------ *
 * 专场渲染（docs/architecture/campaign-rendering.md）
 * 只读已发布版本快照（不可变 revision），草稿变更不影响线上页面；
 * 模块类型是否落在白名单由 @awn/campaign-renderer 判定，未知类型拒绝渲染。
 * ------------------------------------------------------------------ */

/** 模块类型取值（服务端按 schemaVersion 1 白名单下发） */
export type RenderedSectionType =
  | 'heroBanner'
  | 'richText'
  | 'imageGallery'
  | 'videoEmbed'
  | 'eventList'
  | 'ticketGrid'
  | 'ticketComparison'
  | 'priceTrend'
  | 'channelComparison'
  | 'countdown'
  | 'rulePanel'
  | 'faq'
  | 'noticeBar'

export interface RenderedSectionView {
  id: string
  type: string
  props: Record<string, unknown>
  /** 模块正文（富文本纯文本形态）；不存在时为 null */
  content: string | null
}

export interface RenderedPageView {
  schemaVersion: number
  template: string
  seoTitle: string
  seoDescription: string
  sections: RenderedSectionView[]
}

export async function renderCampaign(
  campaignCode: string,
  init?: RequestInit,
): Promise<RenderedPageView> {
  return fetch(`${API_BASE}/campaigns/${campaignCode}/render`, init).then((res) =>
    readJson<RenderedPageView>(res),
  )
}
