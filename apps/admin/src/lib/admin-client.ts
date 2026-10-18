import {
  ApiError,
  createRefund,
  listPayments,
  type DisputeResolution,
  type DisputeSellerResponseView,
  type DisputeView,
  type PaymentView,
  type RefundView,
  type SessionVenueView,
} from '@awn/api-client'

/**
 * 运营接口客户端（契约 docs/api/contracts.md「运营接口」）。
 * 全部走 /api/v1/admin，鉴权头 X-Admin-Token（AdminAuthFilter，缺失/错误 401）；
 * 非 2xx 一律抛 ApiError（复用 @awn/api-client 的错误语义，带 status + 后端错误码）。
 */

const BASE = '/api/v1/admin'

export type AdminOrderStatus =
  | 'PENDING_PAYMENT'
  | 'PAID'
  | 'FULFILLING'
  | 'FULFILLED'
  | 'REFUND_PENDING'
  | 'REFUNDED'
  | 'CANCELLED'
  | 'EXPIRED'

export interface AdminOrderItem {
  quantity: number
  lineTotal: number
  productNameSnapshot: string | null
  ticketCategorySnapshot: string | null
}

export interface AdminOrder {
  orderNo: string
  status: AdminOrderStatus
  currency: string
  totalAmount: number
  discountAmount: number
  feeAmount: number
  payAmount: number
  expiresAt: string | null
  items: AdminOrderItem[]
}

export interface PoolBalance {
  poolCode: string
  totalQuantity: number
  reservedQuantity: number
  soldQuantity: number
  available: number
  version: number
}

export interface AdminEventRow {
  id: string
  eventCode: string
  name: string
  status: string
  startTime: string | null
  endTime: string | null
  venueId: string | null
}

export interface AdminVenue {
  id: string
  venueCode: string
  name: string
  city: string | null
  address: string | null
  latitude: number | null
  longitude: number | null
  /** 位置示意图（首方静态图，非精确地图）；受控存储域白名单，半截标记由服务端拒绝 */
  mapImageUrl: string | null
  mapMarkerX: number | null
  mapMarkerY: number | null
}

/** 场馆配置提交体（PUT /venues/{venueCode} 全量覆盖，null 即清空） */
export interface AdminVenueForm {
  name: string
  city: string | null
  address: string | null
  latitude: number | null
  longitude: number | null
  mapImageUrl: string | null
  mapMarkerX: number | null
  mapMarkerY: number | null
}

/** geo 为解析结果（场次场馆覆盖 → 父活动场馆 → null），null 表示当前无任何场馆 */
export interface AdminSessionRow {
  id: string
  sessionCode: string
  eventId: string
  name: string
  status: string
  startTime: string | null
  endTime: string | null
  geo: SessionVenueView | null
}

export interface AdminAlert {
  id: number
  category: string
  severity: string
  message: string
  reference: string | null
  createdAt: string
}

export interface AuditEntry {
  id: number
  actor: string
  action: string
  targetType: string
  targetId: string
  detail: string | null
  createdAt: string
}

export interface InventoryAdjustment {
  poolCode: string
  delta: number
  reason: string
  actor: string
}

/** 座位（运营视图）：orderNo 为占座订单（公开座位图剥去该字段） */
export interface AdminSeat {
  seatNo: string
  productCode: string
  sessionCode: string
  section: string
  row: string
  seat: string
  orderNo: string | null
  status: 'IN_STOCK' | 'RESERVED' | 'SOLD' | 'DELIVERED' | 'INVALID'
  /** S2 契约先行：可选座位坐标，坐标写入路径落地前恒为 null（运营视图暂不展示） */
  x?: number | null
  y?: number | null
}

/** 铺座批次：一次铺一个商品在同一场次下的座位（整体成功或整体失败） */
export interface SeatCreatePayload {
  productCode: string
  positions: { section: string; row: string; seat: string }[]
}

/** 裁决请求：actor 必填（审计），resolution + note 必填（后端校验） */
export interface DisputeAdjudicatePayload {
  actor: string
  resolution: DisputeResolution
  note: string
}

async function request<T>(path: string, token: string, init: RequestInit = {}): Promise<T> {
  const res = await fetch(`${BASE}${path}`, {
    ...init,
    headers: {
      'x-admin-token': token,
      ...(init.body ? { 'content-type': 'application/json' } : {}),
      ...(init.headers ?? {}),
    },
  })
  const body: unknown = await res.json().catch(() => null)
  if (!res.ok) {
    const envelope = body as { code?: unknown; message?: unknown } | null
    throw new ApiError(
      res.status,
      typeof envelope?.code === 'string' ? envelope.code : `HTTP_${res.status}`,
      typeof envelope?.message === 'string' ? envelope.message : res.statusText,
    )
  }
  return body as T
}

/** 退款执行幂等键：每次提交内容变化后换新键（同键不同内容会被后端判冲突） */
export function newIdempotencyKey(): string {
  const cryptoRef = globalThis.crypto
  if (cryptoRef && typeof cryptoRef.randomUUID === 'function') {
    return cryptoRef.randomUUID()
  }
  return `awn-refund-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`
}

export const adminApi = {
  orders: (token: string) => request<AdminOrder[]>('/orders', token),
  events: (token: string) => request<AdminEventRow[]>('/events', token),
  inventory: (token: string) => request<PoolBalance[]>('/inventory', token),
  auditLogs: (token: string) => request<AuditEntry[]>('/audit-logs', token),
  anomalies: (token: string) => request<AdminAlert[]>('/anomalies', token),

  publishEvent: (token: string, eventCode: string) =>
    request<AdminEventRow>(`/events/${encodeURIComponent(eventCode)}/publish`, token, {
      method: 'POST',
    }),

  adjustInventory: (token: string, payload: InventoryAdjustment) =>
    request<{ poolCode: string; delta: number }>('/inventory/adjustments', token, {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  acknowledgeAlert: (token: string, alertId: number) =>
    request<{ acknowledged: boolean }>(`/anomalies/${alertId}/acknowledge`, token, {
      method: 'POST',
    }),

  venues: (token: string) => request<AdminVenue[]>('/venues', token),

  /** 场馆地理与地图配置：全量覆盖，未知场馆 404，白名单/成对守卫由服务端给 400 */
  updateVenue: (token: string, venueCode: string, form: AdminVenueForm) =>
    request<AdminVenue>(`/venues/${encodeURIComponent(venueCode)}`, token, {
      method: 'PUT',
      body: JSON.stringify(form),
    }),

  eventSessions: (token: string, eventCode: string) =>
    request<AdminSessionRow[]>(`/events/${encodeURIComponent(eventCode)}/sessions`, token),

  attachSessionVenue: (token: string, sessionCode: string, venueCode: string) =>
    request<SessionVenueView | null>(`/sessions/${encodeURIComponent(sessionCode)}/venue`, token, {
      method: 'POST',
      body: JSON.stringify({ venueCode }),
    }),

  detachSessionVenue: (token: string, sessionCode: string) =>
    request<SessionVenueView | null>(`/sessions/${encodeURIComponent(sessionCode)}/venue`, token, {
      method: 'DELETE',
    }),

  sessionSeats: (token: string, sessionCode: string) =>
    request<AdminSeat[]>(`/sessions/${encodeURIComponent(sessionCode)}/seats`, token),

  createSeats: (token: string, sessionCode: string, payload: SeatCreatePayload) =>
    request<AdminSeat[]>(`/sessions/${encodeURIComponent(sessionCode)}/seats`, token, {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  disputes: (token: string) => request<DisputeView[]>('/disputes', token),

  acceptDispute: (token: string, disputeNo: string) =>
    request<DisputeView>(`/disputes/${encodeURIComponent(disputeNo)}/accept`, token, {
      method: 'POST',
    }),

  adjudicateDispute: (token: string, disputeNo: string, payload: DisputeAdjudicatePayload) =>
    request<DisputeView>(`/disputes/${encodeURIComponent(disputeNo)}/adjudicate`, token, {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  disputeSellerResponses: (token: string, disputeNo: string) =>
    request<DisputeSellerResponseView[]>(
      `/disputes/${encodeURIComponent(disputeNo)}/seller-responses`,
      token,
    ),

  /**
   * 退款执行通道（公开交易端点，非 /admin 前缀，不要求运营令牌）：
   * 争议裁决 REFUND_APPROVED 后按既有退款流执行——裁决本身不动钱，此处只是
   * 把「查支付单 → 发起退款」收进争议队列，口径与 POST /orders/{orderNo}/refunds 一致。
   */
  orderPayments: (orderNo: string): Promise<PaymentView[]> =>
    listPayments(encodeURIComponent(orderNo)),

  createOrderRefund: (
    orderNo: string,
    idempotencyKey: string,
    payload: { paymentNo: string; amount: number; reason: string },
  ): Promise<RefundView> => createRefund(orderNo, idempotencyKey, payload),
}
