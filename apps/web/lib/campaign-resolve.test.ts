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
  geo: null,
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

  it('passes through rulePanel with inline display-ready rules', async () => {
    const [resolved] = await resolveSections(
      [section({ type: 'rulePanel', props: { rules: ['实名一票一证', '逾期不退'] } })],
      fakeApi(),
    )
    expect(resolved.status).toBe('ready')
    expect(resolved.props['rules']).toEqual(['实名一票一证', '逾期不退'])
  })

  it('prefers inline rules over a dangling ruleDocumentId', async () => {
    const [resolved] = await resolveSections(
      [section({ type: 'rulePanel', props: { ruleDocumentId: 'rules_001', rules: ['实名一票一证'] } })],
      fakeApi(),
    )
    expect(resolved.status).toBe('ready')
    expect(resolved.props['rules']).toEqual(['实名一票一证'])
  })

  it('keeps rulePanel pending when rules are empty or malformed', async () => {
    const [empty, malformed] = await resolveSections(
      [
        section({ type: 'rulePanel', props: { rules: [] } }),
        section({ type: 'rulePanel', props: { rules: '实名一票一证' } }),
      ],
      fakeApi(),
    )
    expect(empty.status).toBe('pending')
    expect(empty.reason).toContain('格式不符')
    expect(malformed.status).toBe('pending')
    expect(malformed.reason).toContain('格式不符')
  })

  it('passes through faq with display-ready question/answer entries', async () => {
    const [resolved] = await resolveSections(
      [
        section({
          type: 'faq',
          props: { entries: [{ question: '可以退票吗？', answer: '未出票可退。' }] },
        }),
      ],
      fakeApi(),
    )
    expect(resolved.status).toBe('ready')
    expect(resolved.props['entries']).toEqual([{ question: '可以退票吗？', answer: '未出票可退。' }])
  })

  it('keeps faq pending when entries lack question or answer text', async () => {
    const [empty, missing] = await resolveSections(
      [
        section({ type: 'faq', props: { entries: [] } }),
        section({ type: 'faq', props: { entries: [{ title: '不是问答' }] } }),
      ],
      fakeApi(),
    )
    expect(empty.status).toBe('pending')
    expect(empty.reason).toContain('格式不符')
    expect(missing.status).toBe('pending')
    expect(missing.reason).toContain('格式不符')
  })

  it('passes through imageGallery with image entries and alt text', async () => {
    const [resolved] = await resolveSections(
      [
        section({
          type: 'imageGallery',
          props: { images: [{ src: '', alt: '主舞台示意（几何占位）' }] },
        }),
      ],
      fakeApi(),
    )
    expect(resolved.status).toBe('ready')
    expect(resolved.props['images']).toEqual([{ src: '', alt: '主舞台示意（几何占位）' }])
  })

  it('keeps imageGallery pending when images are empty or not objects', async () => {
    const [empty, malformed] = await resolveSections(
      [
        section({ type: 'imageGallery', props: { images: [] } }),
        section({ type: 'imageGallery', props: { images: ['not-an-object'] } }),
      ],
      fakeApi(),
    )
    expect(empty.status).toBe('pending')
    expect(empty.reason).toContain('格式不符')
    expect(malformed.status).toBe('pending')
    expect(malformed.reason).toContain('格式不符')
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
