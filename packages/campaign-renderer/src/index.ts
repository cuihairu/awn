/**
 * 专场模板渲染器。
 *
 * 关键约束（见 docs/architecture/campaign-rendering.md）：
 * - 只读取"已发布版本"（campaign_revisions 快照），草稿变更不影响线上页面；
 * - 模块类型必须命中白名单，未命中一律拒绝渲染（禁止注入任意 JS/HTML）；
 * - 渲染层不得执行库存扣减，也不得覆盖成交价格、退款规则或真实供货状态。
 */

/** 受控模块白名单（schemaVersion 1） */
export const CAMPAIGN_SECTION_TYPES = [
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
] as const

export type CampaignSectionType = (typeof CAMPAIGN_SECTION_TYPES)[number]

export interface CampaignSection {
  id: string
  type: CampaignSectionType
  props: Record<string, unknown>
}

export interface CampaignConfig {
  schemaVersion: 1
  template: string
  seo: { title: string; description: string }
  sections: CampaignSection[]
}

/** 校验专场配置是否可发布 */
export function validateCampaignConfig(config: CampaignConfig): string[] {
  const errors: string[] = []
  if (config.schemaVersion !== 1) errors.push('schemaVersion must be 1')
  if (!config.seo?.title) errors.push('seo.title is required')
  for (const section of config.sections ?? []) {
    if (!CAMPAIGN_SECTION_TYPES.includes(section.type)) {
      errors.push(`unknown section type: ${section.type}`)
    }
  }
  return errors
}

export { CampaignRenderer } from './renderer'
export type { CampaignRendererProps } from './renderer'
