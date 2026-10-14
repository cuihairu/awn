import { afterEach, describe, expect, it, vi } from 'vitest'
import { adminApi } from './admin-client'

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
})
