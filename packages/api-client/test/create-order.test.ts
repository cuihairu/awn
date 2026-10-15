import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  API_BASE,
  cancelOrder,
  createOrder,
  createPayment,
  createRefund,
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
  seatNo: null,
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

describe('seat order lines', () => {
  it('posts one seat line per seat with quantity 1 and no pool or listing', async () => {
    const seatLines = [
      { ...line, poolCode: null, seatNo: 'SEAT-A-1-05', quantity: 1 },
      { ...line, poolCode: null, seatNo: 'SEAT-A-1-06', quantity: 1 },
    ]
    const fetchMock = mockFetchOnce({ orderNo: 'ORD-2' })

    await createOrder({ ...payload, lines: seatLines })

    const { body } = callOf(fetchMock)
    expect(body.lines).toEqual(seatLines)
    // 座位行只认 seatNo（服务端三路径互斥），不得携带 poolCode / listingNo
    for (const l of body.lines) {
      expect(l.seatNo).toMatch(/^SEAT-/)
      expect(l.poolCode).toBeNull()
      expect(l.listingNo).toBeNull()
      expect(l.quantity).toBe(1)
    }
  })
})

describe('attendee real-name lines', () => {
  it('posts per-ticket attendees verbatim (one id per ticket)', async () => {
    const attendees = [
      { name: '张三', idNumber: '11010119900307861X' },
      { name: '李四', idNumber: '310101199502140029' },
    ]
    const fetchMock = mockFetchOnce({ orderNo: 'ORD-3' })

    await createOrder({
      ...payload,
      lines: [{ ...line, quantity: 2, attendees }],
    })

    const { body } = callOf(fetchMock)
    // 一票一证：attendees 数量必须等于行数量（后端 400 守卫）
    expect(body.lines[0].attendees).toEqual(attendees)
    expect(body.lines[0].attendees).toHaveLength(body.lines[0].quantity)
  })

  it('keeps attendee-free lines free of an attendees field', async () => {
    const fetchMock = mockFetchOnce({ orderNo: 'ORD-4' })
    await createOrder({ ...payload, lines: [{ ...line, attendees: null }] })
    expect(callOf(fetchMock).body.lines[0].attendees).toBeNull()
  })

  it('reads masked attendees back from the order view', async () => {
    const fetchMock = mockFetchOnce({
      orderNo: 'ORD-5',
      status: 'PENDING_PAYMENT',
      currency: 'CNY',
      totalAmount: 168000,
      discountAmount: 0,
      feeAmount: 0,
      payAmount: 168000,
      expiresAt: null,
      serverTime: '2026-10-15T11:00:00Z',
      payExpiresInSeconds: 0,
      items: [
        {
          poolCode: 'POOL-1',
          listingNo: null,
          unitNo: null,
          seatNo: null,
          productId: 'prod-001',
          productNameSnapshot: '内场 A 区',
          sessionId: 'sess-001',
          ticketCategorySnapshot: null,
          quantity: 1,
          unitPrice: 168000,
          discountAmount: 0,
          feeAmount: 0,
          lineTotal: 168000,
          reservationNo: 'RES-1',
          // 服务端脱敏值（前 3 后 4），客户端原样展示不反推
          attendees: [{ name: '张三', idNumber: '110***********861X' }],
        },
      ],
    })

    const order = await getOrder('ORD-5')
    expect(order.items[0].attendees).toEqual([
      { name: '张三', idNumber: '110***********861X' },
    ])
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

describe('createRefund', () => {
  it('posts the refund body with the idempotency key in the header only', async () => {
    const refund = {
      refundNo: 'REF-1',
      paymentNo: 'PAY-1',
      orderNo: 'ORD-1',
      channelRefundNo: null,
      status: 'PENDING',
      currency: 'CNY',
      amount: 10000,
      reason: '争议裁决退款',
    }
    const fetchMock = mockFetchOnce(refund)

    const view = await createRefund('ORD-1', 'key-refund-1', {
      paymentNo: 'PAY-1',
      amount: 10000,
      reason: '争议裁决退款',
    })

    const { url, init, headers, body } = callOf(fetchMock)
    expect(url).toBe(`${API_BASE}/orders/ORD-1/refunds`)
    expect(init.method).toBe('POST')
    expect(headers['idempotency-key']).toBe('key-refund-1')
    // 幂等键只走 header；金额为最小货币单位整数，客户端不传渠道（渠道取支付单）
    expect(body).toEqual({ paymentNo: 'PAY-1', amount: 10000, reason: '争议裁决退款' })
    expect(body).not.toHaveProperty('idempotencyKey')
    expect(view.refundNo).toBe('REF-1')
  })

  it('reads the refund back on idempotent replay', async () => {
    const fetchMock = mockFetchOnce({
      refundNo: 'REF-1',
      paymentNo: 'PAY-1',
      orderNo: 'ORD-1',
      channelRefundNo: null,
      status: 'PENDING',
      currency: 'CNY',
      amount: 10000,
      reason: '争议裁决退款',
    })
    const first = await createRefund('ORD-1', 'key-refund-1', {
      paymentNo: 'PAY-1',
      amount: 10000,
      reason: '争议裁决退款',
    })
    const replay = await createRefund('ORD-1', 'key-refund-1', {
      paymentNo: 'PAY-1',
      amount: 10000,
      reason: '争议裁决退款',
    })
    expect(replay.refundNo).toBe(first.refundNo)
    expect(fetchMock).toHaveBeenCalledTimes(2)
  })
})