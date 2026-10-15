import { requestJson, type Transport } from './mp-request'

/**
 * 小程序端 API 客户端（契约 docs/api/contracts.md 公开接口）。
 *
 * transport 由宿主注入（app 启动时 setTransport(taroTransport)），
 * 单测直接 setTransport(内存实现)，模块本身零平台依赖。
 * 订单创建必须携带 Idempotency-Key（同键同请求返回原结果，同键不同请求报冲突）。
 */

let transport: Transport | null = null

export function setTransport(next: Transport): void {
  transport = next
}

function call<T>(options: Parameters<Transport>[0]): Promise<T> {
  if (!transport) return Promise.reject(new Error('transport not set: call setTransport first'))
  return requestJson<T>(transport, options)
}

/* ---------------- 类型（与后端公开响应一一对应） ---------------- */

export type EventStatus = 'DRAFT' | 'PUBLISHED' | 'CANCELLED' | 'COMPLETED'

export interface EventView {
  id: string
  eventCode: string
  name: string
  status: EventStatus
  startTime: string | null
  endTime: string | null
  venueId: string | null
}

export interface SessionView {
  id: string
  sessionCode: string
  eventId: string
  name: string
  status: string
  startTime: string | null
  endTime: string | null
}

export interface ProductListItem {
  productCode: string
  name: string
  status: string
  unitPrice: number
  categoryId: string | null
  sessionId: string | null
  /** 数量行下单所需（POST /orders 的 lines[].poolCode）；未建池为 null */
  poolCode: string | null
  available: number | null
}

export interface OrderLinePayload {
  poolCode: string
  listingNo?: string
  productId: string
  productNameSnapshot: string
  sessionId: string
  ticketCategorySnapshot?: string
  quantity: number
  unitPrice: number
  discountAmount: number
  feeAmount: number
}

export interface OrderView {
  orderNo: string
  status: string
  currency: string
  totalAmount: number
  discountAmount: number
  feeAmount: number
  payAmount: number
  expiresAt: string | null
  items: { quantity: number; lineTotal: number }[]
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

export interface RenderedSection {
  id: string
  type: string
  props: Record<string, unknown>
  content: string | null
}

export interface RenderedPage {
  schemaVersion: number
  template: string
  seoTitle: string
  seoDescription: string | null
  sections: RenderedSection[]
}

/** 公开专场条目（列表只含已发布专场） */
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

/** 素材条目：区块 props 的 assetIds 按 id 匹配 */
export interface CampaignAssetView {
  id: number
  assetType: 'IMAGE' | 'VIDEO' | 'BANNER' | 'THUMBNAIL'
  url: string
  altText: string | null
  displayOrder: number
}

/* ---------------- 端点 ---------------- */

export const mpApi = {
  /** 公开活动列表（仅 PUBLISHED / COMPLETED，开始时间倒序） */
  listEvents: () => call<EventView[]>({ url: '/api/v1/events', method: 'GET' }),

  getEvent: (eventId: string) =>
    call<EventView>({ url: `/api/v1/events/${eventId}`, method: 'GET' }),

  /** 公开场次列表（活动页选场次；父活动未公开 → 404） */
  listEventSessions: (eventId: string) =>
    call<SessionView[]>({ url: `/api/v1/events/${eventId}/sessions`, method: 'GET' }),

  getSession: (sessionId: string) =>
    call<SessionView>({ url: `/api/v1/sessions/${sessionId}`, method: 'GET' }),

  /** 场次票档（仅已发布商品；available 为缓存展示口径） */
  getSessionProducts: (sessionId: string) =>
    call<ProductListItem[]>({ url: `/api/v1/sessions/${sessionId}/products`, method: 'GET' }),

  /** 公开专场列表（只含已发布专场，按最近变更倒序） */
  listCampaigns: () => call<CampaignView[]>({ url: '/api/v1/campaigns', method: 'GET' }),

  /** 已发布专场渲染页（只读已发布版本，草稿不影响线上） */
  renderCampaign: (campaignCode: string) =>
    call<RenderedPage>({ url: `/api/v1/campaigns/${campaignCode}/render`, method: 'GET' }),

  /** 须知条目（只读已发布专场；未发布 → 404） */
  getCampaignRules: (campaignCode: string) =>
    call<CampaignRuleView[]>({ url: `/api/v1/campaigns/${campaignCode}/rules`, method: 'GET' }),

  /** 常见问题条目（只读已发布专场；未发布 → 404） */
  getCampaignFaq: (campaignCode: string) =>
    call<CampaignFaqView[]>({ url: `/api/v1/campaigns/${campaignCode}/faq`, method: 'GET' }),

  /** 素材条目（只读已发布专场；未发布 → 404） */
  getCampaignAssets: (campaignCode: string) =>
    call<CampaignAssetView[]>({ url: `/api/v1/campaigns/${campaignCode}/assets`, method: 'GET' }),

  /** 创建订单（必须带 Idempotency-Key；金额由服务端重算） */
  createOrder: (idempotencyKey: string, userId: string, lines: OrderLinePayload[]) =>
    call<OrderView>({
      url: '/api/v1/orders',
      method: 'POST',
      header: { 'Idempotency-Key': idempotencyKey },
      data: { userId, currency: 'CNY', lines },
    }),

  getOrder: (orderNo: string) =>
    call<OrderView>({ url: `/api/v1/orders/${orderNo}`, method: 'GET' }),

  /** 发起支付（金额/币种服务端取订单快照，客户端只选渠道） */
  createPayment: (orderNo: string, channel: string) =>
    call<PaymentView>({
      url: `/api/v1/orders/${orderNo}/payments`,
      method: 'POST',
      data: { channel },
    }),

  listPayments: (orderNo: string) =>
    call<PaymentView[]>({ url: `/api/v1/orders/${orderNo}/payments`, method: 'GET' }),

  cancelOrder: (orderNo: string) =>
    call<OrderView>({ url: `/api/v1/orders/${orderNo}/cancel`, method: 'POST' }),
}
