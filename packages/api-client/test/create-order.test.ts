import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  API_BASE,
  cancelOrder,
  createOrder,
  createPayment,
  getFulfillment,
  getOrder,
  listPayments,
  quoteCheckout,
  type OrderCreatePayload,
} from '../src/index'

/**
 * 交易客户端契约（docs/api/contracts.md 交易接口）：
 * 下单必须带 Idempency-Key 头且键不进请求体；金额一律服务端重算，客户端只传单价与优惠；
 * 支付创建只传渠道——金额与币种由服务端取订单快照，前端传多少都不参与计算。
 */

const line = {
  poolCode: 'POOL-1',
  listingNo: null,
  productId: 'prod-001',
  productNameSnapshot: '内场 A 区',
  sessionId: 'sess-001',
  ticketCategorySnapshot: null,
  quantity: 2,
  unitPrice: 168000,
  discountAmount: 0,
  feeAmount: 0,
}

const payload: OrderCreatePayload = {
  idempotencyKey: '7c9e6679-7425-40de-944b-e07fc1f90ae7',
  userId: '9001',
  currency: 'CNY',
  lines: [line],
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

function callOf(fn: ReturnType<typeof mockFetchOnce>) {
  // GET 类调用直接把 init 透传给 fetch，第二次参数可能是 undefined
  const [url, rawInit] = fn.mock.calls[0] as [string, RequestInit | undefined]
  const init: RequestInit = rawInit ?? {}
  return {
    url,
    init,
    headers: (init.headers ?? {}) as Record<string, string>,
    body: init.body ? JSON.parse(String(init.body)) : undefined,
  }
}

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('createOrder', () => {
  it('posts the order body with the idempotency key in the header only', async () => {
    const order = {
      orderNo: 'ORD-1',
      status: 'PENDING_PAYMENT',
      currency: 'CNY',
      totalAmount: 336000,
      discountAmount: 0,
      feeAmount: 0,
      payAmount: 336000,
      expiresAt: '2026-11-06T12:00:00Z',
      items: [],
    }
    const fetchMock = mockFetchOnce(order)

    await expect(createOrder(payload)).resolves.toEqual(order)

    const { url, init, headers, body } = callOf(fetchMock)
    expect(url).toBe(`${API_BASE}/orders`)
    expect(init.method).toBe('POST')
    expect(headers['idempotency-key']).toBe(payload.idempotencyKey)
    // 幂等键只走 header，不进请求体（后端按头判重）
    expect(body).toEqual({
      userId: '9001',
      currency: 'CNY',
      lines: [line],
    })
  })

  it('never sends a client-side total amount', async () => {
    const fetchMock = mockFetchOnce({})
    await createOrder(payload)
    const { body } = callOf(fetchMock)
    expect(body).not.toHaveProperty('payAmount')
    expect(body).not.toHaveProperty('totalAmount')
  })

  it('lets callers override headers and init', async () => {
    const fetchMock = mockFetchOnce({})

    await createOrder(payload, {
      headers: { 'x-trace-id': 'trace-1' },
      cache: 'no-store',
    })

    const { init, headers } = callOf(fetchMock)
    expect(headers['x-trace-id']).toBe('trace-1')
    expect(headers['idempotency-key']).toBe(payload.idempotencyKey)
    expect(init.cache).toBe('no-store')
  })
})

describe('quoteCheckout', () => {
  it('posts the same lines to the quote endpoint without an idempotency key', async () => {
    const fetchMock = mockFetchOnce({ currency: 'CNY', payAmount: 336000 })

    await quoteCheckout({ currency: 'CNY', lines: [line] })

    const { url, init, headers, body } = callOf(fetchMock)
    expect(url).toBe(`${API_BASE}/checkout/quote`)
    expect(init.method).toBe('POST')
    expect(headers['idempotency-key']).toBeUndefined()
    expect(body).toEqual({ currency: 'CNY', lines: [line] })
  })
})

describe('order reads and payment', () => {
  it('reads an order by order number', async () => {
    const fetchMock = mockFetchOnce({ orderNo: 'ORD-1' })
    await getOrder('ORD-1')
    expect(callOf(fetchMock).url).toBe(`${API_BASE}/orders/ORD-1`)
  })

  it('cancels an order with POST', async () => {
    const fetchMock = mockFetchOnce({ orderNo: 'ORD-1', status: 'CANCELLED' })
    const order = await cancelOrder('ORD-1')
    const { url, init } = callOf(fetchMock)
    expect(url).toBe(`${API_BASE}/orders/ORD-1/cancel`)
    expect(init.method).toBe('POST')
    expect(order.status).toBe('CANCELLED')
  })

  it('creates a payment with the channel only', async () => {
    const fetchMock = mockFetchOnce({
      paymentNo: 'PAY-1',
      orderNo: 'ORD-1',
      channel: 'sandbox',
      channelTxnNo: null,
      status: 'PENDING',
      currency: 'CNY',
      amount: 336000,
      paidAt: null,
    })

    await createPayment('ORD-1', 'sandbox')

    const { url, init, body } = callOf(fetchMock)
    expect(url).toBe(`${API_BASE}/orders/ORD-1/payments`)
    expect(init.method).toBe('POST')
    expect(body).toEqual({ channel: 'sandbox' })
  })

  it('lists payment history for an order', async () => {
    const fetchMock = mockFetchOnce([])
    await expect(listPayments('ORD-1')).resolves.toEqual([])
    expect(callOf(fetchMock).url).toBe(`${API_BASE}/orders/ORD-1/payments`)
  })

  it('reads the fulfillment record by order number', async () => {
    const fetchMock = mockFetchOnce({
      fulfillmentNo: 'FUL-1',
      orderNo: 'ORD-1',
      status: 'PENDING_SELLER',
      failReason: null,
    })
    const fulfillment = await getFulfillment('ORD-1')
    expect(callOf(fetchMock).url).toBe(`${API_BASE}/orders/ORD-1/fulfillment`)
    expect(fulfillment.status).toBe('PENDING_SELLER')
  })
})