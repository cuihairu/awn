import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  API_BASE,
  getEvent,
  getProduct,
  getProductAvailability,
  getSeatmap,
  getSession,
  getSessionProducts,
  listEventSessions,
  listEvents,
} from '../src/index'

/**
 * 活动 / 场次 / 商品客户端契约（docs/api/contracts.md 公开接口）：
 * 路径与后端 PublicEventController / PublicProductController 一一对应；
 * 非 2xx 抛 ApiError（公开面未发布与不存在一律 404 信封）。
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

describe('events / sessions / products', () => {
  it('lists published events from the public face', async () => {
    const fetchMock = mockFetchOnce([])
    await listEvents()
    expect(fetchMock).toHaveBeenCalledWith(`${API_BASE}/events`, undefined)
  })

  it('lists sessions of an event for the activity page', async () => {
    const fetchMock = mockFetchOnce([])
    await listEventSessions('3')
    expect(fetchMock).toHaveBeenCalledWith(
      `${API_BASE}/events/3/sessions`,
      undefined,
    )
  })

  it('reads one event', async () => {
    const fetchMock = mockFetchOnce({ id: '3', eventCode: 'EVT-1' })
    await getEvent('3')
    expect(fetchMock).toHaveBeenCalledWith(`${API_BASE}/events/3`, undefined)
  })

  it('reads one session', async () => {
    const fetchMock = mockFetchOnce({ id: '9', sessionCode: 'SES-1' })
    await getSession('9')
    expect(fetchMock).toHaveBeenCalledWith(`${API_BASE}/sessions/9`, undefined)
  })

  it('lists session products', async () => {
    const fetchMock = mockFetchOnce([])
    await getSessionProducts('9')
    expect(fetchMock).toHaveBeenCalledWith(
      `${API_BASE}/sessions/9/products`,
      undefined,
    )
  })

  it('reads product detail and availability', async () => {
    const fetchMock = mockFetchOnce({ productId: 'p-1', sellable: true })
    await getProductAvailability('p-1')
    expect(fetchMock).toHaveBeenCalledWith(
      `${API_BASE}/products/p-1/availability`,
      undefined,
    )
    const detailMock = mockFetchOnce({ productCode: 'p-1' })
    await getProduct('p-1')
    expect(detailMock).toHaveBeenCalledWith(`${API_BASE}/products/p-1`, undefined)
  })

  it('rejects with ApiError when the session is unknown or unpublished', async () => {
    mockFetchOnce(
      { code: 'SESSION_NOT_FOUND', message: 'session not found: 9' },
      { ok: false, status: 404, statusText: 'Not Found' },
    )
    await expect(getSessionProducts('9')).rejects.toMatchObject({
      name: 'ApiError',
      status: 404,
      code: 'SESSION_NOT_FOUND',
    })
  })

  it('reads the session seatmap entries', async () => {
    const fetchMock = mockFetchOnce([
      {
        seatNo: 'SEAT-1',
        productCode: 'p-seat',
        section: 'A区',
        row: '1',
        seat: '1',
        status: 'IN_STOCK',
      },
    ])
    const seats = await getSeatmap('9')
    expect(fetchMock).toHaveBeenCalledWith(`${API_BASE}/sessions/9/seatmap`, undefined)
    expect(seats).toHaveLength(1)
    expect(seats[0]).toMatchObject({ productCode: 'p-seat', status: 'IN_STOCK' })
  })

  it('rejects seatmap read when the parent event is not public', async () => {
    mockFetchOnce(
      { code: 'SESSION_NOT_FOUND', message: 'session not found: 9' },
      { ok: false, status: 404, statusText: 'Not Found' },
    )
    await expect(getSeatmap('9')).rejects.toMatchObject({
      name: 'ApiError',
      status: 404,
      code: 'SESSION_NOT_FOUND',
    })
  })
})
