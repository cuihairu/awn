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
})
