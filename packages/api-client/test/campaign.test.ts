import { afterEach, describe, expect, it, vi } from 'vitest'
import { API_BASE, ApiError, renderCampaign } from '../src/index'

/**
 * 专场渲染客户端契约（docs/architecture/campaign-rendering.md）：
 * 只读 /campaigns/{code}/render，返回已发布版本快照；
 * 未发布 / 未知专场按后端错误信封抛 ApiError，页面据此 404。
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

describe('renderCampaign', () => {
  it('reads the published revision render payload', async () => {
    const page = {
      schemaVersion: 1,
      template: 'concert',
      seoTitle: '星河巡回 深圳站',
      seoDescription: '限量粉丝专场',
      sections: [
        { id: 's1', type: 'heroBanner', props: { title: '星河巡回' }, content: null },
        { id: 's2', type: 'rulePanel', props: { rules: ['实名购票'] }, content: '购票须知' },
      ],
    }
    const fetchMock = mockFetchOnce(page)
    await expect(renderCampaign('star-2026')).resolves.toEqual(page)
    expect(fetchMock).toHaveBeenCalledWith(
      `${API_BASE}/campaigns/star-2026/render`,
      undefined,
    )
  })

  it('raises ApiError with the envelope code for an unpublished campaign', async () => {
    mockFetchOnce(
      { code: 'CAMPAIGN_NOT_FOUND', message: 'campaign not found: draft-1' },
      { ok: false, status: 404 },
    )
    await expect(renderCampaign('draft-1')).rejects.toMatchObject({
      name: 'ApiError',
      status: 404,
      code: 'CAMPAIGN_NOT_FOUND',
    } satisfies Partial<ApiError>)
  })

  it('keeps section props as an opaque record for the renderer whitelist', async () => {
    mockFetchOnce({
      schemaVersion: 1,
      template: 'concert',
      seoTitle: '',
      seoDescription: '',
      sections: [{ id: 'x', type: 'arbitraryHtml', props: { html: '<b>x</b>' }, content: null }],
    })
    const page = await renderCampaign('c-1')
    expect(page.sections[0].type).toBe('arbitraryHtml')
    expect(page.sections[0].props).toEqual({ html: '<b>x</b>' })
  })
})