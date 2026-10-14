import { describe, expect, it, vi } from 'vitest'
import { ApiError, type ProductListItem, type SessionView } from '@awn/api-client'
import type { CampaignSection } from '@awn/campaign-renderer'
import { resolveSections, type CampaignDataApi } from './campaign-resolve'

/**
 * 专场模块解析层契约（docs/architecture/campaign-rendering.md）：
 * 后端存引用型 props（eventIds / ruleDocumentId / faqId），渲染器读展示型
 * （sessions / tiers / paragraphs）；解析层按引用查公开 API 组装，
 * 查不到就标记待接入，不渲染空白区块也不造假内容。
 */

const session: SessionView = {
  id: '9',
  sessionCode: 'SES-01',
  eventId: '1',
  name: '排位赛',
  status: 'PUBLISHED',
  startTime: '2026-11-06T11:30:00Z',
  endTime: '2026-11-06T13:30:00Z',
}

const product: ProductListItem = {
  productCode: 'p-1',
  name: '内场 A 区',
  status: 'PUBLISHED',
  unitPrice: 168000,
  categoryId: null,
  sessionId: '9',
  poolCode: 'POOL-1',
  available: 120,
}

const section = (overrides: Partial<CampaignSection> = {}): CampaignSection => ({
  id: 's1',
  type: 'eventList',
  props: {},
  ...overrides,
})

const fakeApi = (overrides: Partial<CampaignDataApi> = {}): CampaignDataApi => ({
  listEventSessions: vi.fn(async () => [session]),
  getSessionProducts: vi.fn(async () => [product]),
  ...overrides,
})

describe('resolveSections', () => {
  it('resolves eventList references into session cards', async () => {
    const [resolved] = await resolveSections(
      [section({ type: 'eventList', props: { eventIds: ['1'] } })],
      fakeApi(),
    )
    expect(resolved.status).toBe('ready')
    expect(resolved.props['sessions']).toEqual([
      { name: '排位赛', startAt: expect.stringMatching(/月.*日/) },
    ])
  })

  it('resolves ticketGrid references into tiers with minor-unit prices', async () => {
    const [resolved] = await resolveSections(
      [section({ type: 'ticketGrid', props: { eventIds: ['1'] } })],
      fakeApi(),
    )
    expect(resolved.status).toBe('ready')
    expect(resolved.props['tiers']).toEqual([{ name: '内场 A 区', price: 168000 }])
  })

  it('maps richText html content onto paragraphs', async () => {
    const [resolved] = await resolveSections(
      [section({ type: 'richText', props: { content: '<div class="rich-text">第一段</div>' } })],
      fakeApi(),
    )
    expect(resolved.status).toBe('ready')
    expect(resolved.props['paragraphs']).toEqual(['第一段'])
  })

  it('maps countdown endTime onto until', async () => {
    const [resolved] = await resolveSections(
      [section({ type: 'countdown', props: { endTime: '2026-12-31T20:00:00Z' } })],
      fakeApi(),
    )
    expect(resolved.status).toBe('ready')
    expect(resolved.props['until']).toBe('2026-12-31T20:00:00Z')
  })

  it('passes through sections without external data sources', async () => {
    const [hero, notice] = await resolveSections(
      [
        section({ type: 'heroBanner', props: { title: 'F1 赛事专场' } }),
        section({ type: 'noticeBar', props: { message: '本场次门票数量有限' } }),
      ],
      fakeApi(),
    )
    expect(hero.status).toBe('ready')
    expect(hero.props['title']).toBe('F1 赛事专场')
    expect(notice.status).toBe('ready')
    expect(notice.props['message']).toBe('本场次门票数量有限')
  })

  it('marks sections without a public data source as pending with a reason', async () => {
    const resolutions = await resolveSections(
      [
        section({ type: 'rulePanel', props: { ruleDocumentId: 'rules_001' } }),
        section({ type: 'faq', props: { faqId: 'faq_001' } }),
        section({ type: 'imageGallery', props: { assetIds: ['asset_001'] } }),
        section({ type: 'priceTrend', props: { eventId: '1' } }),
      ],
      fakeApi(),
    )
    expect(resolutions.map((r) => r.status)).toEqual(['pending', 'pending', 'pending', 'pending'])
    for (const resolution of resolutions) {
      expect(resolution.reason).toBeTruthy()
    }
  })

  it('marks eventList pending when the referenced event is not public', async () => {
    const api = fakeApi({
      listEventSessions: vi.fn(async () => {
        throw new ApiError(404, 'EVENT_NOT_FOUND', 'event not found: 404')
      }),
    })
    const [resolved] = await resolveSections(
      [section({ type: 'eventList', props: { eventIds: ['404'] } })],
      api,
    )
    expect(resolved.status).toBe('pending')
    expect(resolved.reason).toBe('引用的活动不存在或未公开')
  })

  it('propagates unexpected failures instead of hiding them', async () => {
    const api = fakeApi({
      listEventSessions: vi.fn(async () => {
        throw new Error('network down')
      }),
    })
    await expect(
      resolveSections([section({ type: 'eventList', props: { eventIds: ['1'] } })], api),
    ).rejects.toThrow('network down')
  })
})
