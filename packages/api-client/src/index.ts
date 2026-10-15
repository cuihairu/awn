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

/** 订单行来源三选一：数量型走 poolCode，凭证型走 listingNo，座位型走 seatNo */
export interface OrderLinePayload {
  poolCode: string | null
  listingNo: string | null
  seatNo: string | null
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
  seatNo: string | null
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
  /** 响应生成时的服务端时间：倒计时以此为锚点，客户端不用本机时钟判超时 */
  serverTime: string
  /** 剩余支付秒数；非待支付或已过期恒为 0 */
  payExpiresInSeconds: number
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
  /** 本单失败且订单仍可支付时为 true：据此显示「可重新支付 / 可换渠道」，不据此推断支付结果 */
  retryable: boolean
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

/** 可用支付渠道编码（由后端已注册网关决定）；渠道编码不硬编码在各端，换渠道前先读本清单 */
export async function listPaymentChannels(
  init?: RequestInit,
): Promise<string[]> {
  return fetch(`${API_BASE}/payments/channels`, init).then((res) =>
    readJson<string[]>(res),
  )
}

/**
 * 失败支付单重试：金额与币种仍取订单应付快照，客户端只选渠道。
 * 原支付单保持 FAILED，历史不改写；未知支付单 404，源单非失败态或订单已过截止期 409。
 */
export async function retryPayment(
  paymentNo: string,
  channel?: string,
  init?: RequestInit,
): Promise<PaymentView> {
  return fetch(`${API_BASE}/payments/${paymentNo}/retry`, {
    ...init,
    method: 'POST',
    headers: {
      'content-type': 'application/json',
      ...(init?.headers ?? {}),
    },
    body: JSON.stringify(channel ? { channel } : {}),
  }).then((res) => readJson<PaymentView>(res))
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

/** 场次地理位置（场馆派生：场次场馆覆盖 → 父活动场馆；坐标缺失为 null，不按 (0,0) 处理） */
export interface SessionVenueView {
  venueCode: string
  venueName: string
  city: string | null
  address: string | null
  latitude: number | null
  longitude: number | null
}

export interface SessionView {
  id: string
  sessionCode: string
  eventId: string
  name: string
  status: SessionStatus
  startTime: string
  endTime: string
  geo: SessionVenueView | null
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

/** 逐座状态机（座位图 status 取值，与库存模块 SeatStatus 一致） */
export type SeatStatus = 'IN_STOCK' | 'RESERVED' | 'SOLD' | 'DELIVERED' | 'INVALID'

/** 座位图条目：只含编号与状态（占座订单号不进公开面） */
export interface SeatMapItem {
  seatNo: string
  productCode: string
  section: string
  row: string
  seat: string
  status: SeatStatus
}

/** 场次座位图（公开只读）：可见性与场次详情同口径，父活动未公开 → 404 */
export async function getSeatmap(
  sessionId: string,
  init?: RequestInit,
): Promise<SeatMapItem[]> {
  return fetch(`${API_BASE}/sessions/${sessionId}/seatmap`, init)
    .then((res) => readJson<SeatMapItem[]>(res))
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

/* ------------------------------------------------------------------ *
 * 专场列表与内容（docs/api/contracts.md「专场列表与内容接口」）
 * 公开面只含已发布专场：草稿 / 已撤回 / 已归档不出现在列表里，
 * 内容端点与渲染同口径，未发布专场一律 404（不泄露草稿）。
 * ------------------------------------------------------------------ */

export interface CampaignView {
  id: number
  campaignCode: string
  title: string
  subtitle: string | null
  status: string
  currentRevisionId: number | null
}

/** 须知条目：区块 props 的 ruleDocumentId 按 id 匹配 */
export interface CampaignRuleView {
  id: number
  title: string
  /** 纯文本，段落以空行分隔 */
  content: string
}

/** 常见问题条目：区块 props 的 faqId 按 id 匹配 */
export interface CampaignFaqView {
  id: number
  question: string
  answer: string
}

export type CampaignAssetType = 'IMAGE' | 'VIDEO' | 'BANNER' | 'THUMBNAIL'

/** 素材条目：区块 props 的 assetIds 按 id 匹配；altText 缺失时前端出占位块 */
export interface CampaignAssetView {
  id: number
  assetType: CampaignAssetType
  url: string
  altText: string | null
  displayOrder: number
}

/** 公开专场列表（只含 PUBLISHED，按最近变更倒序） */
export async function listCampaigns(init?: RequestInit): Promise<CampaignView[]> {
  return fetch(`${API_BASE}/campaigns`, init).then((res) => readJson<CampaignView[]>(res))
}

export async function getCampaignRules(
  campaignCode: string,
  init?: RequestInit,
): Promise<CampaignRuleView[]> {
  return fetch(`${API_BASE}/campaigns/${campaignCode}/rules`, init).then((res) =>
    readJson<CampaignRuleView[]>(res),
  )
}

export async function getCampaignFaq(
  campaignCode: string,
  init?: RequestInit,
): Promise<CampaignFaqView[]> {
  return fetch(`${API_BASE}/campaigns/${campaignCode}/faq`, init).then((res) =>
    readJson<CampaignFaqView[]>(res),
  )
}

export async function getCampaignAssets(
  campaignCode: string,
  init?: RequestInit,
): Promise<CampaignAssetView[]> {
  return fetch(`${API_BASE}/campaigns/${campaignCode}/assets`, init).then((res) =>
    readJson<CampaignAssetView[]>(res),
  )
}

/* ------------------------------------------------------------------ *
 * 排队 / 候补（docs/api/contracts.md 排队 / 候补）
 * 排队只登记意愿：不占库存、不预生成订单；递补由后端 Worker 按池 FIFO 驱动，
 * 为队首原子预留并生成待支付订单（与普通下单同源）。
 * ------------------------------------------------------------------ */

export type WaitlistStatus = 'WAITING' | 'PROMOTED' | 'CLAIMED' | 'EXPIRED' | 'CANCELLED'

export interface WaitlistView {
  waitlistNo: string
  status: WaitlistStatus
  userId: string
  sessionId: string
  poolCode: string
  productId: string
  productName: string
  quantity: number
  unitPrice: number
  currency: string
  /** 同池 WAITING 队列位次（从 1 起）；非排队状态为 null */
  position: number | null
  /** 递补生成的订单号；排队中为 null */
  orderNo: string | null
  /** 递补后订单的剩余支付秒数；未递补为 null */
  payExpiresInSeconds: number | null
  createdAt: string
}

export interface JoinWaitlistPayload {
  sessionId: string
  poolCode: string
  productId: string
  productName: string
  quantity: number
  unitPrice: number
  currency: string
  userId: number
}

/** 加入候补：同用户同池重复排队 → 409 WAITLIST_CONFLICT；未知库存池 → 404 POOL_NOT_FOUND */
export async function joinWaitlist(
  payload: JoinWaitlistPayload,
  init?: RequestInit,
): Promise<WaitlistView> {
  return fetch(`${API_BASE}/waitlist`, {
    ...init,
    method: 'POST',
    headers: {
      'content-type': 'application/json',
      ...(init?.headers ?? {}),
    },
    body: JSON.stringify(payload),
  }).then((res) => readJson<WaitlistView>(res))
}

/** 候补条目查询；未知条目 → 404 WAITLIST_NOT_FOUND */
export async function getWaitlist(
  waitlistNo: string,
  init?: RequestInit,
): Promise<WaitlistView> {
  return fetch(`${API_BASE}/waitlist/${waitlistNo}`, init).then((res) =>
    readJson<WaitlistView>(res),
  )
}

/** 某用户的候补列表（加入时间倒序）；缺 userId → 400 VALIDATION_FAILED */
export async function listMyWaitlist(
  userId: string,
  init?: RequestInit,
): Promise<WaitlistView[]> {
  return fetch(`${API_BASE}/waitlist?userId=${encodeURIComponent(userId)}`, init).then(
    (res) => readJson<WaitlistView[]>(res),
  )
}

/**
 * 退出排队：重复取消幂等返回 canceled=false；已递补或已终态 → 409 WAITLIST_CONFLICT
 * （已递补的席位走订单取消 POST /orders/{orderId}/cancel）。
 */
export async function cancelWaitlist(
  waitlistNo: string,
  init?: RequestInit,
): Promise<{ canceled: boolean }> {
  return fetch(`${API_BASE}/waitlist/${waitlistNo}/cancel`, {
    ...init,
    method: 'POST',
  }).then((res) => readJson<{ canceled: boolean }>(res))
}
