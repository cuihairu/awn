import { afterEach, describe, expect, it, vi } from 'vitest'
import { API_BASE, createOrder, type OrderCreatePayload } from '../src/index'

/**
 * 下单客户端契约（docs/api/contracts.md）：
 * POST /api/v1/orders 必须携带 Idempotency-Key；幂等语义由后端保证，
 * 客户端只负责把键放进 header 并把 payload 序列化成 JSON。
 */
const payload: OrderCreatePayload = {
  idempotencyKey: '7c9e6679-7425-40de-944b-e07fc1f90ae7',
  lines: [{ productId: 'prod-001', sessionId: 'sess-001', quantity: 2 }],
}

function mockFetchOnce(body: unknown) {
  const fn = vi.fn().mockResolvedValue({
    ok: true,
    status: 200,
    statusText: 'OK',
    json: () => Promise.resolve(body),
  })
  vi.stubGlobal('fetch', fn)
  return fn
}

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('createOrder', () => {
  it('posts the payload to the orders endpoint with the idempotency key header', async () => {
    const fetchMock = mockFetchOnce({ orderId: 'o-1', status: 'PENDING_PAYMENT' })

    const result = await createOrder(payload)

    expect(result).toEqual({ orderId: 'o-1', status: 'PENDING_PAYMENT' })
    expect(fetchMock).toHaveBeenCalledTimes(1)
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(url).toBe(`${API_BASE}/orders`)
    expect(init.method).toBe('POST')
    expect((init.headers as Record<string, string>)['idempotency-key']).toBe(
      payload.idempotencyKey,
    )
    expect(JSON.parse(String(init.body))).toEqual(payload)
  })

  it('lets callers override headers and init', async () => {
    const fetchMock = mockFetchOnce({})

    await createOrder(payload, {
      headers: { 'x-trace-id': 'trace-1' },
      cache: 'no-store',
    })

    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect((init.headers as Record<string, string>)['x-trace-id']).toBe('trace-1')
    expect((init.headers as Record<string, string>)['idempotency-key']).toBe(
      payload.idempotencyKey,
    )
    expect(init.cache).toBe('no-store')
  })
})
