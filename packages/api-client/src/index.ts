/**
 * AWN API 客户端。
 *
 * 契约来源：docs/api/contracts.md（/api/v1）。
 * 订单创建必须携带 Idempotency-Key；相同幂等键 + 相同请求返回原结果，
 * 相同幂等键 + 不同请求返回冲突错误。
 */

export const API_BASE = '/api/v1'

export interface OrderCreatePayload {
  /** 客户端生成的幂等键 */
  idempotencyKey: string
  lines: { productId: string; sessionId: string; quantity: number }[]
}

export interface OrderStatus {
  orderId: string
  status:
    | 'PENDING_PAYMENT'
    | 'PAID'
    | 'FULFILLING'
    | 'FULFILLED'
    | 'REFUND_PENDING'
    | 'REFUNDED'
    | 'CANCELLED'
    | 'EXPIRED'
  amountMinor: number
  currency: 'CNY'
}

export async function createOrder(
  payload: OrderCreatePayload,
  init?: RequestInit,
): Promise<unknown> {
  return fetch(`${API_BASE}/orders`, {
    ...init,
    method: 'POST',
    headers: {
      'content-type': 'application/json',
      'idempotency-key': payload.idempotencyKey,
      ...(init?.headers ?? {}),
    },
    body: JSON.stringify(payload),
  }).then((res) => res.json())
}
