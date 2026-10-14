import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  API_BASE,
  ApiError,
  getPerson,
  getWork,
  listPersons,
  listWorks,
} from '../src/index'

/**
 * 图鉴客户端契约（docs/api/contracts.md）：
 * 公开读只暴露 PUBLISHED；空值参数不下发；q 命中姓名 / 别名 / 原名与标题。
 * 非 2xx 一律抛 ApiError（带 status 与后端错误码），不把错误信封当成功体返回。
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

describe('listPersons', () => {
  it('sends profession, nationality and q params', async () => {
    const fetchMock = mockFetchOnce([])
    await listPersons({ profession: 'actor', nationality: '中国', q: '沈流舒' })
    expect(fetchMock).toHaveBeenCalledWith(
      `${API_BASE}/stars?profession=actor&nationality=%E4%B8%AD%E5%9B%BD&q=%E6%B2%88%E6%B5%81%E8%88%92`,
      undefined,
    )
  })

  it('omits empty params instead of sending blank query values', async () => {
    const fetchMock = mockFetchOnce([])
    await listPersons({ profession: '', nationality: '  ', q: '' })
    expect(fetchMock).toHaveBeenCalledWith(`${API_BASE}/stars`, undefined)
  })

  it('trims whitespace around param values', async () => {
    const fetchMock = mockFetchOnce([])
    await listPersons({ q: '  长风  ' })
    expect(fetchMock).toHaveBeenCalledWith(`${API_BASE}/stars?q=%E9%95%BF%E9%A3%8E`, undefined)
  })
})

describe('listWorks', () => {
  it('sends type and q params', async () => {
    const fetchMock = mockFetchOnce([])
    await listWorks({ type: 'movie', q: '春山夜行' })
    expect(fetchMock).toHaveBeenCalledWith(
      `${API_BASE}/works?type=movie&q=%E6%98%A5%E5%B1%B1%E5%A4%9C%E8%A1%8C`,
      undefined,
    )
  })

  it('omits empty params', async () => {
    const fetchMock = mockFetchOnce([])
    await listWorks({})
    expect(fetchMock).toHaveBeenCalledWith(`${API_BASE}/works`, undefined)
  })
})

describe('getPerson / getWork', () => {
  it('reads the person detail from the public face', async () => {
    const fetchMock = mockFetchOnce({ person: {}, works: [] })
    await getPerson('7')
    expect(fetchMock).toHaveBeenCalledWith(`${API_BASE}/stars/7`, undefined)
  })

  it('reads the work detail from the public face', async () => {
    const fetchMock = mockFetchOnce({ work: {}, cast: [] })
    await getWork('9')
    expect(fetchMock).toHaveBeenCalledWith(`${API_BASE}/works/9`, undefined)
  })
})

describe('error envelope', () => {
  it('rejects with ApiError carrying status and code on 404', async () => {
    mockFetchOnce(
      { code: 'PERSON_NOT_FOUND', message: 'person not found: 7' },
      { ok: false, status: 404, statusText: 'Not Found' },
    )
    await expect(getPerson('7')).rejects.toMatchObject({
      name: 'ApiError',
      status: 404,
      code: 'PERSON_NOT_FOUND',
    })
  })

  it('never resolves a list with an error envelope (would crash array rendering)', async () => {
    mockFetchOnce(
      { code: 'RATE_LIMITED', message: 'too many requests' },
      { ok: false, status: 429, statusText: 'Too Many Requests' },
    )
    await expect(listPersons()).rejects.toBeInstanceOf(ApiError)
  })

  it('falls back to an HTTP code when the body is not an error envelope', async () => {
    mockFetchOnce(null, { ok: false, status: 502, statusText: 'Bad Gateway' })
    await expect(listWorks()).rejects.toMatchObject({ status: 502, code: 'HTTP_502' })
  })
})
