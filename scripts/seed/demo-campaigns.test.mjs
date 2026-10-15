import { describe, expect, it } from 'vitest'
import { existsSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { demoCampaigns } from './demo-campaigns.mjs'

/**
 * 三套专场实例种子内容（todo 141 余留）的契约测试。
 *
 * 内容全部取自已批准原型 docs/design/prototypes/web/activity-{f1,jay,zcx}.html
 * （原型过目已代决 2026-10-14，demo-note 标注「数据为示例」）。
 * 本测试守住三条线：
 * 1. 结构合法——schemaVersion / template / 区块类型白名单 / id 唯一，保证能过后端
 *    CampaignConfigValidator（modules/awn-campaign/.../CampaignConfigValidator.java）；
 * 2. 素材红线——不出现任何媒体引用键（url / src / poster / assetId）与
 *    imageGallery / videoEmbed 区块，不生成图片代餐；
 * 3. 内容真实——每个实例的 source 指向的原型文件必须存在，不虚构出处。
 */

// 与后端 SectionWhitelist / @awn/campaign-renderer CAMPAIGN_SECTION_TYPES 同口径
const SECTION_TYPES = [
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
]

// 素材红线：这些键的值必须是白名单域 URL，种子内容一律不出现
const MEDIA_KEYS = ['url', 'src', 'poster', 'assetId']

const FORBIDDEN_SECTION_TYPES = ['imageGallery', 'videoEmbed']

function collectStrings(value, out = []) {
  if (typeof value === 'string') out.push(value)
  else if (Array.isArray(value)) value.forEach((item) => collectStrings(item, out))
  else if (value && typeof value === 'object') Object.values(value).forEach((item) => collectStrings(item, out))
  return out
}

function collectKeys(value, out = []) {
  if (Array.isArray(value)) value.forEach((item) => collectKeys(item, out))
  else if (value && typeof value === 'object') {
    for (const [key, child] of Object.entries(value)) {
      out.push(key)
      collectKeys(child, out)
    }
  }
  return out
}

describe('demo campaign seed content', () => {
  it('contains exactly the three approved instances', () => {
    expect(demoCampaigns.map((instance) => instance.campaignCode)).toEqual([
      'f1-2026-china',
      'jay-carnival-2026',
      'zcx-shibuzhigui-2026',
    ])
  })

  it('titles match the approved home prototype rows', () => {
    expect(demoCampaigns.map((instance) => instance.title)).toEqual([
      '2026 F1 中国大奖赛',
      '周杰伦「嘉年华」世界巡回演唱会',
      '周传雄「时不知归」巡回演唱会',
    ])
  })

  it('every instance cites an existing prototype file as its source', () => {
    for (const instance of demoCampaigns) {
      const path = fileURLToPath(new URL(`../../${instance.source}`, import.meta.url))
      expect(existsSync(path), `missing prototype: ${instance.source}`).toBe(true)
    }
  })

  it('configs pass the backend validator shape (schemaVersion, template, unique ids, whitelisted types)', () => {
    for (const instance of demoCampaigns) {
      const config = instance.config
      expect(config.schemaVersion).toBe(1)
      expect(['motorsport', 'concert']).toContain(config.template)
      expect(config.seo.title).toBe(instance.title)
      expect(config.seo.description.length).toBeGreaterThan(0)
      expect(config.sections.length).toBeGreaterThan(0)

      const ids = config.sections.map((section) => section.id)
      expect(new Set(ids).size).toBe(ids.length)
      for (const section of config.sections) {
        expect(SECTION_TYPES).toContain(section.type)
      }
    }
  })

  it('honors the material red line: no media reference keys and no media sections', () => {
    for (const instance of demoCampaigns) {
      const types = instance.config.sections.map((section) => section.type)
      for (const type of FORBIDDEN_SECTION_TYPES) {
        expect(types, `${instance.campaignCode} must not contain ${type}`).not.toContain(type)
      }
      const keys = collectKeys(instance.config)
      for (const key of MEDIA_KEYS) {
        expect(keys, `${instance.campaignCode} must not reference ${key}`).not.toContain(key)
      }
    }
  })

  it('carries no script or embedded HTML (server validator mirror)', () => {
    for (const instance of demoCampaigns) {
      for (const text of collectStrings(instance.config)) {
        const lower = text.toLowerCase()
        expect(lower).not.toContain('<script')
        expect(lower).not.toContain('javascript:')
        expect(lower).not.toContain('<iframe')
        expect(lower).not.toContain('<object')
        expect(lower).not.toContain('<embed')
      }
    }
  })

  it('renders every section with non-empty display props', () => {
    for (const instance of demoCampaigns) {
      for (const section of instance.config.sections) {
        if (section.type === 'heroBanner') {
          expect(section.props.title.length).toBeGreaterThan(0)
          expect(section.props.subtitle.length).toBeGreaterThan(0)
        } else if (section.type === 'richText') {
          expect(section.props.title.length).toBeGreaterThan(0)
          expect(Array.isArray(section.props.paragraphs)).toBe(true)
          expect(section.props.paragraphs.length).toBeGreaterThan(0)
          expect(section.props.paragraphs.every((p) => typeof p === 'string' && p.length > 0)).toBe(true)
        } else if (section.type === 'rulePanel') {
          expect(Array.isArray(section.props.rules)).toBe(true)
          expect(section.props.rules.length).toBeGreaterThan(0)
          expect(section.props.rules.every((r) => typeof r === 'string' && r.length > 0)).toBe(true)
        } else if (section.type === 'noticeBar') {
          expect(section.props.message.length).toBeGreaterThan(0)
        } else if (section.type === 'eventList' || section.type === 'ticketGrid') {
          expect(Array.isArray(section.props.eventIds)).toBe(true)
          expect(section.props.eventIds.length).toBeGreaterThan(0)
          expect(section.props.eventIds.every((id) => typeof id === 'string')).toBe(true)
        }
      }
    }
  })

  it('keeps the placeholder event reference documented and unresolved', () => {
    // 场次 / 票档区块引用占位活动 id '0'（不存在）：resolve 层按待接入渲染，
    // 运营创建活动并回填真实 eventId 前不出现假数据。
    for (const instance of demoCampaigns) {
      const referencing = instance.config.sections.filter(
        (section) => section.type === 'eventList' || section.type === 'ticketGrid',
      )
      expect(referencing.length).toBeGreaterThan(0)
      for (const section of referencing) {
        expect(section.props.eventIds).toEqual(['0'])
      }
    }
  })
})
