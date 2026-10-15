import { afterEach, describe, expect, it, vi } from 'vitest'
import { adminApi, newIdempotencyKey } from './admin-client'

/**
 * 运营客户端契约：鉴权头 X-Admin-Token 必带；
 * 非 2xx（401 令牌错误 / 409 状态机冲突）抛 ApiError 携带后端错误码。
 */

function mockFetchOnce(
  body: unknown,
  res: { ok?: boolean; status?: number; statusText?: string } = {},
) {
  const fn = vi.fn().mockResolvedValue({
    ok: res.ok ?? true,
    status: res.status ?? 200,
    statusText: res.statusText ?? 'OK',
    json: () => Promise.resolve(body),
  })
  vi.stubGlobal('fetch', fn)
  return fn
}

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('admin client', () => {
  it('sends the admin token header on list calls', async () => {
    const fetchMock = mockFetchOnce([])
    await adminApi.orders('secret-token')
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/v1/admin/orders')
    expect(init.headers['x-admin-token']).toBe('secret-token')
  })

  it('posts adjustments with json body and token', async () => {
    const fetchMock = mockFetchOnce({ poolCode: 'P1', delta: 5 })
    await adminApi.adjustInventory('t', { poolCode: 'P1', delta: 5, reason: '盘点', actor: 'cui' })
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/v1/admin/inventory/adjustments')
    expect(init.method).toBe('POST')
    expect(init.headers['content-type']).toBe('application/json')
    expect(init.body).toContain('"delta":5')
  })

  it('encodes the event code on publish', async () => {
    const fetchMock = mockFetchOnce({ eventCode: 'E 1', status: 'PUBLISHED' })
    await adminApi.publishEvent('t', 'E 1')
    expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/admin/events/E%201/publish')
  })

  it('lists venues and event sessions for the session config view', async () => {
    const venuesMock = mockFetchOnce([])
    await adminApi.venues('t')
    expect(venuesMock.mock.calls[0][0]).toBe('/api/v1/admin/venues')
    expect(venuesMock.mock.calls[0][1].headers['x-admin-token']).toBe('t')

    const sessionsMock = mockFetchOnce([])
    await adminApi.eventSessions('t', 'e-1')
    expect(sessionsMock.mock.calls[0][0]).toBe('/api/v1/admin/events/e-1/sessions')
  })

  it('attaches a session venue with a json body and encodes the session code', async () => {
    const fetchMock = mockFetchOnce({ venueCode: 'v-1', city: '上海' })
    await adminApi.attachSessionVenue('t', 's 1', 'v-1')
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/v1/admin/sessions/s%201/venue')
    expect(init.method).toBe('POST')
    expect(init.body).toBe('{"venueCode":"v-1"}')
  })

  it('detaches a session venue and tolerates an empty fallback body', async () => {
    const fetchMock = mockFetchOnce(null)
    const geo = await adminApi.detachSessionVenue('t', 's-1')
    expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/admin/sessions/s-1/venue')
    expect(fetchMock.mock.calls[0][1].method).toBe('DELETE')
    expect(geo).toBeNull()
  })

  it('rejects with ApiError carrying backend code on failure', async () => {
    mockFetchOnce(
      { code: 'UNAUTHORIZED', message: 'admin token required' },
      { ok: false, status: 401, statusText: 'Unauthorized' },
    )
    await expect(adminApi.anomalies('wrong')).rejects.toMatchObject({
      name: 'ApiError',
      status: 401,
      code: 'UNAUTHORIZED',
    })
  })

  it('lists session seats with the operational order column', async () => {
    const fetchMock = mockFetchOnce([
      {
        seatNo: 'SEAT-1',
        productCode: 'p-seat',
        sessionCode: 's-1',
        section: 'A',
        row: '1',
        seat: '1',
        orderNo: 'ORD-1',
        status: 'RESERVED',
      },
    ])
    const seats = await adminApi.sessionSeats('t', 's 1')
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/v1/admin/sessions/s%201/seats')
    expect(init.headers['x-admin-token']).toBe('t')
    expect(seats[0].orderNo).toBe('ORD-1')
    expect(seats[0].status).toBe('RESERVED')
  })

  it('creates seats with the product code and position batch', async () => {
    const fetchMock = mockFetchOnce([])
    await adminApi.createSeats('t', 's-1', {
      productCode: 'p-seat',
      positions: [
        { section: 'A', row: '1', seat: '1' },
        { section: 'A', row: '1', seat: '2' },
      ],
    })
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/v1/admin/sessions/s-1/seats')
    expect(init.method).toBe('POST')
    expect(init.headers['content-type']).toBe('application/json')
    expect(JSON.parse(String(init.body))).toEqual({
      productCode: 'p-seat',
      positions: [
        { section: 'A', row: '1', seat: '1' },
        { section: 'A', row: '1', seat: '2' },
      ],
    })
  })

  it('lists recent disputes with the admin token', async () => {
    const fetchMock = mockFetchOnce([])
    await adminApi.disputes('secret-token')
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/v1/admin/disputes')
    expect(init.method).toBeUndefined()
    expect(init.headers['x-admin-token']).toBe('secret-token')
  })

  it('accepts a dispute with an encoded dispute number', async () => {
    const fetchMock = mockFetchOnce({ disputeNo: 'DSP 1', status: 'UNDER_REVIEW' })
    const view = await adminApi.acceptDispute('t', 'DSP 1')
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/v1/admin/disputes/DSP%201/accept')
    expect(init.method).toBe('POST')
    expect(init.headers['x-admin-token']).toBe('t')
    expect(view.status).toBe('UNDER_REVIEW')
  })

  it('adjudicates a dispute with actor, resolution and note', async () => {
    const fetchMock = mockFetchOnce({ disputeNo: 'DSP-1', status: 'RESOLVED' })
    const view = await adminApi.adjudicateDispute('t', 'DSP-1', {
      actor: 'admin-1',
      resolution: 'REFUND_APPROVED',
      note: '证据充分，支持退款',
    })
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/v1/admin/disputes/DSP-1/adjudicate')
    expect(init.method).toBe('POST')
    expect(init.headers['content-type']).toBe('application/json')
    expect(JSON.parse(String(init.body))).toEqual({
      actor: 'admin-1',
      resolution: 'REFUND_APPROVED',
      note: '证据充分，支持退款',
    })
    expect(view.status).toBe('RESOLVED')
  })

  it('lists seller responses of a dispute for adjudication', async () => {
    const fetchMock = mockFetchOnce([
      { disputeNo: 'DSP-1', sellerNo: 'SEL-1', content: '已交付凭证', createdAt: '2026-10-15T09:00:00Z' },
    ])
    const responses = await adminApi.disputeSellerResponses('t', 'DSP 1')
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/v1/admin/disputes/DSP%201/seller-responses')
    expect(init.headers['x-admin-token']).toBe('t')
    expect(responses[0].sellerNo).toBe('SEL-1')
    expect(responses[0].content).toBe('已交付凭证')
  })

  it('rejects with ApiError when dispute state machine conflicts', async () => {
    mockFetchOnce(
      { code: 'DISPUTE_CONFLICT', message: 'dispute is not open for seller responses' },
      { ok: false, status: 409, statusText: 'Conflict' },
    )
    await expect(adminApi.acceptDispute('t', 'DSP-1')).rejects.toMatchObject({
      name: 'ApiError',
      status: 409,
      code: 'DISPUTE_CONFLICT',
    })
  })
})

describe('refund execution channel', () => {
  it('lists order payments through the public trade endpoint', async () => {
    const fetchMock = mockFetchOnce([
      {
        paymentNo: 'PAY-1',
        orderNo: 'ORD-1',
        channel: 'sandbox',
        channelTxnNo: null,
        status: 'SUCCEEDED',
        currency: 'CNY',
        amount: 10000,
        paidAt: null,
        retryable: false,
      },
    ])
    const payments = await adminApi.orderPayments('ORD 1')
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit | undefined]
    // 退款执行走公开交易端点（非 /admin 前缀），不要求运营令牌
    expect(url).toBe('/api/v1/orders/ORD%201/payments')
    expect(init).toBeUndefined()
    expect(payments[0].status).toBe('SUCCEEDED')
  })

  it('creates a refund with the idempotency key header and minor-unit body', async () => {
    const fetchMock = mockFetchOnce({
      refundNo: 'REF-1',
      paymentNo: 'PAY-1',
      orderNo: 'ORD-1',
      channelRefundNo: null,
      status: 'PENDING',
      currency: 'CNY',
      amount: 10000,
      reason: '争议 DSP-1 裁决退款',
    })
    const refund = await adminApi.createOrderRefund('ORD-1', 'key-1', {
      paymentNo: 'PAY-1',
      amount: 10000,
      reason: '争议 DSP-1 裁决退款',
    })
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/v1/orders/ORD-1/refunds')
    expect(init.method).toBe('POST')
    expect(init.headers['idempotency-key']).toBe('key-1')
    expect(init.headers['content-type']).toBe('application/json')
    expect(JSON.parse(String(init.body))).toEqual({
      paymentNo: 'PAY-1',
      amount: 10000,
      reason: '争议 DSP-1 裁决退款',
    })
    expect(refund.refundNo).toBe('REF-1')
  })

  it('rejects a refund failure with the backend error code', async () => {
    mockFetchOnce(
      { code: 'REFUND_CONFLICT', message: 'refundable limit exceeded' },
      { ok: false, status: 409, statusText: 'Conflict' },
    )
    await expect(
      adminApi.createOrderRefund('ORD-1', 'key-2', {
        paymentNo: 'PAY-1',
        amount: 999999,
        reason: 'r',
      }),
    ).rejects.toMatchObject({ name: 'ApiError', status: 409, code: 'REFUND_CONFLICT' })
  })

  it('generates unique idempotency keys', () => {
    const keys = new Set(Array.from({ length: 50 }, () => newIdempotencyKey()))
    expect(keys.size).toBe(50)
  })
})
