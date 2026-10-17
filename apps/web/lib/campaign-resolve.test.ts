import { describe, expect, it, vi } from 'vitest'
import {
  ApiError,
  type CampaignAssetView,
  type CampaignFaqView,
  type CampaignRuleView,
  type EventPricePointView,
  type ProductListItem,
  type SessionView,
} from '@awn/api-client'
import type { CampaignSection } from '@awn/campaign-renderer'
import { PENDING_SECTION_LABELS, resolveSections, type CampaignDataApi } from './campaign-resolve'

/**
 * 专场模块解析层契约（docs/architecture/campaign-rendering.md）：
 * 后端存引用型 props（eventIds / ruleDocumentId / faqId / assetIds），渲染器读展示型
 * （sessions / tiers / paragraphs / rules / entries / images）；解析层按引用查公开 API 组装，
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

/** 公开价格历史（按观测时间倒序）：后端为「分」口径，币种与来源随条目下发 */
const pricePoints: EventPricePointView[] = [
  {
    price: 48000,
    currency: 'CNY',
    availability: 'SELLING',
    sourceNo: 'SRC-1',
    sourceName: '大麦网',
    kind: 'PLATFORM',
    trustTier: 1,
    observedAt: '2026-11-06T11:00:00Z',
  },
  {
    price: 52000,
    currency: 'CNY',
    availability: 'SELLING',
    sourceNo: 'SRC-3',
    sourceName: '官方渠道',
    kind: 'OFFICIAL',
    trustTier: 1,
    observedAt: '2026-11-05T11:00:00Z',
  },
  {
    price: 36000,
    currency: 'CNY',
    availability: 'SOLD_OUT',
    sourceNo: 'SRC-2',
    sourceName: '猫眼',
    kind: 'PLATFORM',
    trustTier: 2,
    observedAt: '2026-11-04T11:00:00Z',
  },
]

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

const rule: CampaignRuleView = { id: 11, title: '购票须知', content: '实名一票一证\n\n逾期不退' }
const faq: CampaignFaqView = { id: 21, question: '可以退票吗？', answer: '未出票可退。' }
const otherFaq: CampaignFaqView = { id: 22, question: '如何取票？', answer: '开演前 1 小时取票。' }
const asset: CampaignAssetView = {
  id: 31,
  assetType: 'IMAGE',
  url: 'https://cdn.example.com/a.jpg',
  altText: '主舞台示意',
  displayOrder: 1,
}

/** 注入了内容端点的假接口（须知 / 常见问题 / 素材各一条） */
const contentApi = (overrides: Partial<CampaignDataApi> = {}): CampaignDataApi =>
  fakeApi({
    getCampaignRules: vi.fn(async () => [rule]),
    getCampaignFaq: vi.fn(async () => [faq, otherFaq]),
    getCampaignAssets: vi.fn(async () => [asset]),
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

  it('resolves priceTrend references into a summary from the public price history', async () => {
    const api = fakeApi({ getEventPrices: vi.fn(async () => pricePoints) })
    const [resolved] = await resolveSections(
      [section({ type: 'priceTrend', props: { eventId: '1' } })],
      api,
    )
    expect(resolved.status).toBe('ready')
    expect(api.getEventPrices).toHaveBeenCalledWith('1')
    expect(resolved.props['summary']).toBe(
      '最近观测 480.00 CNY（11 月 6 日 19:00 · 大麦网），共 3 条记录，区间 360.00–520.00',
    )
  })

  it('prefers an inline summary over the referenced price history', async () => {
    const api = fakeApi({ getEventPrices: vi.fn(async () => pricePoints) })
    const [resolved] = await resolveSections(
      [
        section({
          type: 'priceTrend',
          props: { eventId: '1', summary: '官方渠道放票前价格逐轮上浮' },
        }),
      ],
      api,
    )
    expect(resolved.status).toBe('ready')
    expect(resolved.props['summary']).toBe('官方渠道放票前价格逐轮上浮')
    expect(api.getEventPrices).not.toHaveBeenCalled()
  })

  it('omits the range claim when there is one observation or mixed currencies', async () => {
    const [single, mixed] = await resolveSections(
      [
        section({ type: 'priceTrend', props: { eventId: '1' } }),
        section({ type: 'priceTrend', props: { eventId: '2' } }),
      ],
      fakeApi({
        getEventPrices: vi.fn(async (eventId: string) =>
          eventId === '1'
            ? pricePoints.slice(0, 1)
            : [pricePoints[0], { ...pricePoints[1], currency: 'HKD' }, pricePoints[2]],
        ),
      }),
    )
    expect(single.status).toBe('ready')
    expect(single.props['summary']).toBe('最近观测 480.00 CNY（11 月 6 日 19:00 · 大麦网），共 1 条记录')
    expect(mixed.status).toBe('ready')
    expect(mixed.props['summary']).not.toContain('区间')
  })

  it('marks priceTrend pending when the event is unknown or has no observations', async () => {
    const [notPublic, empty] = await resolveSections(
      [
        section({ type: 'priceTrend', props: { eventId: '404' } }),
        section({ type: 'priceTrend', props: { eventId: '1' } }),
      ],
      fakeApi({
        getEventPrices: vi.fn(async (eventId: string) => {
          if (eventId === '404') {
            throw new ApiError(404, 'EVENT_NOT_FOUND', 'event not found: 404')
          }
          return []
        }),
      }),
    )
    expect(notPublic.status).toBe('pending')
    expect(notPublic.reason).toBe('引用的活动不存在或未公开')
    expect(empty.reason).toBe('引用的活动暂无价格观测数据')
  })

  it('keeps priceTrend pending without an inline summary or an event reference', async () => {
    const [resolved] = await resolveSections(
      [section({ type: 'priceTrend', props: {} })],
      fakeApi(),
    )
    expect(resolved.status).toBe('pending')
    expect(resolved.reason).toBe('价格趋势未接入（未配置 summary 或 eventId）')
  })

  it('propagates network errors from the price endpoint', async () => {
    const api = fakeApi({
      getEventPrices: vi.fn(async () => {
        throw new Error('network down')
      }),
    })
    await expect(
      resolveSections([section({ type: 'priceTrend', props: { eventId: '1' } })], api),
    ).rejects.toThrow('network down')
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
      'CAMP-1',
    )
    expect(resolutions.map((r) => r.status)).toEqual(['pending', 'pending', 'pending', 'pending'])
    for (const resolution of resolutions) {
      expect(resolution.reason).toBeTruthy()
    }
  })

  it('resolves ruleDocumentId against the campaign rules endpoint', async () => {
    const api = contentApi()
    const [resolved] = await resolveSections(
      [section({ type: 'rulePanel', props: { ruleDocumentId: '11' } })],
      api,
      'CAMP-1',
    )
    expect(resolved.status).toBe('ready')
    // 正文按空行分段，渲染器读的是 rules 文本数组
    expect(resolved.props['rules']).toEqual(['实名一票一证', '逾期不退'])
    expect(api.getCampaignRules).toHaveBeenCalledWith('CAMP-1')
  })

  it('resolves faqId and faqIds to question/answer entries', async () => {
    const [single, multiple] = await resolveSections(
      [
        section({ type: 'faq', props: { faqId: '21' } }),
        section({ type: 'faq', props: { faqIds: ['22', '21'] } }),
      ],
      contentApi(),
      'CAMP-1',
    )
    expect(single.status).toBe('ready')
    expect(single.props['entries']).toEqual([{ question: '可以退票吗？', answer: '未出票可退。' }])
    // 多条按引用顺序取，不按列表顺序
    expect(multiple.props['entries']).toEqual([
      { question: '如何取票？', answer: '开演前 1 小时取票。' },
      { question: '可以退票吗？', answer: '未出票可退。' },
    ])
  })

  it('resolves assetIds to image entries in reference order', async () => {
    const api = contentApi({
      getCampaignAssets: vi.fn(async () => [
        asset,
        { ...asset, id: 32, url: 'https://cdn.example.com/b.jpg', altText: '看台示意' },
        // 缺 alt 的素材不出图（素材红线）
        { ...asset, id: 33, altText: null },
      ]),
    })
    const [resolved] = await resolveSections(
      [section({ type: 'imageGallery', props: { assetIds: ['32', '33', '31'] } })],
      api,
      'CAMP-1',
    )
    expect(resolved.status).toBe('ready')
    expect(resolved.props['images']).toEqual([
      { src: 'https://cdn.example.com/b.jpg', alt: '看台示意' },
      { src: 'https://cdn.example.com/a.jpg', alt: '主舞台示意' },
    ])
  })

  it('keeps reference lookups pending when the ids do not match any entry', async () => {
    const resolutions = await resolveSections(
      [
        section({ type: 'rulePanel', props: { ruleDocumentId: '999' } }),
        section({ type: 'faq', props: { faqId: '999' } }),
        section({ type: 'imageGallery', props: { assetIds: ['999'] } }),
      ],
      contentApi(),
      'CAMP-1',
    )
    expect(resolutions.map((r) => r.status)).toEqual(['pending', 'pending', 'pending'])
    expect(resolutions[0].reason).toContain('999')
    expect(resolutions[1].reason).toContain('faqId')
    expect(resolutions[2].reason).toContain('assetIds')
  })

  it('treats content endpoint failures as not found but propagates network errors', async () => {
    const notFound = contentApi({
      getCampaignRules: vi.fn(async () => {
        throw new ApiError(404, 'CAMPAIGN_NOT_FOUND', 'campaign not found: 404')
      }),
    })
    const [resolved] = await resolveSections(
      [section({ type: 'rulePanel', props: { ruleDocumentId: '11' } })],
      notFound,
      'CAMP-1',
    )
    expect(resolved.status).toBe('pending')

    const broken = contentApi({
      getCampaignAssets: vi.fn(async () => {
        throw new Error('network down')
      }),
    })
    await expect(
      resolveSections(
        [section({ type: 'imageGallery', props: { assetIds: ['31'] } })],
        broken,
        'CAMP-1',
      ),
    ).rejects.toThrow('network down')
  })

  it('keeps content sections pending when neither inline props nor references are set', async () => {
    const resolutions = await resolveSections(
      [
        section({ type: 'rulePanel', props: {} }),
        section({ type: 'faq', props: {} }),
        section({ type: 'imageGallery', props: {} }),
      ],
      contentApi(),
      'CAMP-1',
    )
    expect(resolutions.map((r) => r.status)).toEqual(['pending', 'pending', 'pending'])
    expect(resolutions.map((r) => r.reason)).toEqual([
      '须知内容未接入（未配置 rules 或 ruleDocumentId）',
      '常见问题内容未接入（未配置 entries 或 faqId）',
      '图片素材未接入（未配置 images 或 assetIds）',
    ])
  })

  it('does not query content endpoints without a campaign code', async () => {
    const api = contentApi()
    const [resolved] = await resolveSections(
      [section({ type: 'rulePanel', props: { ruleDocumentId: '11' } })],
      api,
    )
    expect(api.getCampaignRules).not.toHaveBeenCalled()
    expect(resolved.status).toBe('pending')
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

  it('labels every pending section type in Chinese for the page display', () => {
    // 种子实例（scripts/seed/demo-campaigns.mjs）的场次 / 票档区块在活动回填前
    // 长期处于待接入态，标签不能回落到英文类型名
    for (const type of [
      'eventList',
      'ticketGrid',
      'rulePanel',
      'faq',
      'imageGallery',
      'priceTrend',
      'ticketComparison',
      'channelComparison',
      'videoEmbed',
    ]) {
      expect(PENDING_SECTION_LABELS[type], `missing label for ${type}`).toBeTruthy()
    }
  })
})
