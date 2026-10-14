import { ApiError } from '@awn/api-client'

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
}
