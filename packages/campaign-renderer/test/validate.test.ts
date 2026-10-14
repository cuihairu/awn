import { describe, expect, it } from 'vitest'
import {
  CAMPAIGN_SECTION_TYPES,
  validateCampaignConfig,
  type CampaignConfig,
} from '../src/index'

/**
 * 专场配置校验契约（docs/architecture/campaign-rendering.md）：
 * schemaVersion 固定 1、seo.title 必填、模块类型必须命中白名单。
 */
describe('CAMPAIGN_SECTION_TYPES', () => {
  it('lists exactly the 13 whitelisted section types (parity with backend SectionWhitelist)', () => {
    expect(CAMPAIGN_SECTION_TYPES).toEqual([
      'heroBanner',
      'richText',
      'imageGallery',
      'videoEmbed',
      'eventList',
      'ticketGrid',
      'ticketComparison',
      'priceTrend',
      'channelComparison',
      'countdown',
      'rulePanel',
      'faq',
      'noticeBar',
    ])
  })
})

const validConfig: CampaignConfig = {
  schemaVersion: 1,
  template: 'motorsport',
  seo: { title: 'AWN F1 专场', description: '赛事票务' },
  sections: [
    { id: 'hero', type: 'heroBanner', props: { assetId: 'asset_001' } },
    { id: 'tickets', type: 'ticketGrid', props: {} },
  ],
}

describe('validateCampaignConfig', () => {
  it('accepts a well-formed config', () => {
    expect(validateCampaignConfig(validConfig)).toEqual([])
  })

  it('rejects a schemaVersion other than 1', () => {
    const errors = validateCampaignConfig({ ...validConfig, schemaVersion: 2 })
    expect(errors).toContain('schemaVersion must be 1')
  })

  it('requires seo.title', () => {
    const errors = validateCampaignConfig({
      ...validConfig,
      seo: { title: '', description: '' },
    })
    expect(errors).toContain('seo.title is required')
  })

  it('rejects section types outside the whitelist', () => {
    const errors = validateCampaignConfig({
      ...validConfig,
      sections: [{ id: 'x', type: 'customWidget', props: {} }],
    })
    expect(errors).toContain('unknown section type: customWidget')
  })

  it('collects multiple errors at once', () => {
    const errors = validateCampaignConfig({
      schemaVersion: 3,
      template: 'concert',
      seo: { title: '', description: '' },
      sections: [
        { id: 'a', type: 'richText', props: {} },
        { id: 'b', type: 'evilWidget', props: {} },
      ],
    })
    expect(errors).toEqual([
      'schemaVersion must be 1',
      'seo.title is required',
      'unknown section type: evilWidget',
    ])
  })
})
