import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  API_BASE,
  getPerson,
  getWork,
  listPersons,
  listWorks,
} from '../src/index'

/**
 * 图鉴客户端契约（docs/api/contracts.md）：
 * 公开读只暴露 PUBLISHED；空值参数不下发；q 命中姓名 / 别名 / 原名与标题。
 */

function mockFetchOnce(url: string, body: unknown) {
  const fn = vi.fn().mockResolvedValue({ json: () => Promise.resolve(body) })
  vi.stubGlobal('fetch', fn)
  return fn
}

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('listPersons', () => {
  it('sends profession, nationality and q params', async () => {
    const fetchMock = mockFetchOnce(`${API_BASE}/stars`, [])
    await listPersons({ profession: 'actor', nationality: '中国', q: '沈流舒' })
    expect(fetchMock).toHaveBeenCalledWith(
      `${API_BASE}/stars?profession=actor&nationality=%E4%B8%AD%E5%9B%BD&q=%E6%B2%88%E6%B5%81%E8%88%92`,
      undefined,
    )
  })

  it('omits empty params instead of sending blank query values', async () => {
    const fetchMock = mockFetchOnce(`${API_BASE}/stars`, [])
    await listPersons({ profession: '', nationality: '  ', q: '' })
    expect(fetchMock).toHaveBeenCalledWith(`${API_BASE}/stars`, undefined)
  })

  it('trims whitespace around param values', async () => {
    const fetchMock = mockFetchOnce(`${API_BASE}/stars`, [])
    await listPersons({ q: '  长风  ' })
    expect(fetchMock).toHaveBeenCalledWith(`${API_BASE}/stars?q=%E9%95%BF%E9%A3%8E`, undefined)
  })
})

describe('listWorks', () => {
  it('sends type and q params', async () => {
    const fetchMock = mockFetchOnce(`${API_BASE}/works`, [])
    await listWorks({ type: 'movie', q: '春山夜行' })
    expect(fetchMock).toHaveBeenCalledWith(
      `${API_BASE}/works?type=movie&q=%E6%98%A5%E5%B1%B1%E5%A4%9C%E8%A1%8C`,
      undefined,
    )
  })

  it('omits empty params', async () => {
    const fetchMock = mockFetchOnce(`${API_BASE}/works`, [])
    await listWorks({})
    expect(fetchMock).toHaveBeenCalledWith(`${API_BASE}/works`, undefined)
  })
})

describe('getPerson / getWork', () => {
  it('reads the person detail from the public face', async () => {
    const fetchMock = mockFetchOnce(`${API_BASE}/stars/7`, { person: {}, works: [] })
    await getPerson('7')
    expect(fetchMock).toHaveBeenCalledWith(`${API_BASE}/stars/7`, undefined)
  })

  it('reads the work detail from the public face', async () => {
    const fetchMock = mockFetchOnce(`${API_BASE}/works/9`, { work: {}, cast: [] })
    await getWork('9')
    expect(fetchMock).toHaveBeenCalledWith(`${API_BASE}/works/9`, undefined)
  })
})
