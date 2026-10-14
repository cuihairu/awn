import { describe, expect, it } from 'vitest'
import type { RenderedPageView } from '@awn/api-client'
import { CAMPAIGN_SECTION_TYPES } from '@awn/campaign-renderer'
import { toCampaignConfig } from './campaign-config'

/**
 * 专场页适配层契约（docs/architecture/campaign-rendering.md）：
 * 服务端只下发已发布版本快照；模块类型必须命中白名单，
 * 未命中不渲染（记录 skipped），props 只接受对象形态。
 */

const page = (overrides: Partial<RenderedPageView> = {}): RenderedPageView => ({
  schemaVersion: 1,
  template: 'concert',
  seoTitle: '星河巡回 深圳站',
  seoDescription: '限量粉丝专场',
  sections: [],
  ...overrides,
})

describe('toCampaignConfig', () => {
  it('maps the render payload onto the renderer config shape', () => {
    const { config, skipped } = toCampaignConfig(
      page({
        sections: [{ id: 's1', type: 'heroBanner', props: { title: '星河巡回' }, content: null }],
      }),
    )
    expect(config.schemaVersion).toBe(1)
    expect(config.template).toBe('concert')
    expect(config.seo).toEqual({ title: '星河巡回 深圳站', description: '限量粉丝专场' })
    expect(config.sections).toEqual([
      { id: 's1', type: 'heroBanner', props: { title: '星河巡回' } },
    ])
    expect(skipped).toEqual([])
  })

  it('drops sections whose type misses the whitelist and records them', () => {
    const { config, skipped } = toCampaignConfig(
      page({
        sections: [
          { id: 'ok', type: 'faq', props: {}, content: null },
          { id: 'bad', type: 'arbitraryHtml', props: { html: '<b>x</b>' }, content: null },
        ],
      }),
    )
    expect(config.sections.map((s) => s.id)).toEqual(['ok'])
    expect(skipped).toEqual([{ id: 'bad', type: 'arbitraryHtml' }])
  })

  it('accepts every whitelisted type without dropping it', () => {
    const { config, skipped } = toCampaignConfig(
      page({
        sections: CAMPAIGN_SECTION_TYPES.map((type, i) => ({
          id: `s${i}`,
          type,
          props: {},
          content: null,
        })),
      }),
    )
    expect(config.sections).toHaveLength(CAMPAIGN_SECTION_TYPES.length)
    expect(skipped).toEqual([])
  })

  it('falls back to an empty props object when the payload has none', () => {
    const { config } = toCampaignConfig(
      page({
        sections: [
          { id: 's1', type: 'richText', props: null as never, content: null },
          { id: 's2', type: 'richText', props: undefined as never, content: null },
        ],
      }),
    )
    expect(config.sections.map((s) => s.props)).toEqual([{}, {}])
  })

  it('tolerates a payload without sections instead of throwing', () => {
    const { config, skipped } = toCampaignConfig(page({ sections: null as never }))
    expect(config.sections).toEqual([])
    expect(skipped).toEqual([])
  })
})