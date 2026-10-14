import {
  CAMPAIGN_SECTION_TYPES,
  type CampaignConfig,
  type CampaignSection,
  type CampaignSectionType,
} from '@awn/campaign-renderer'
import type { RenderedPageView } from '@awn/api-client'

/**
 * 服务端渲染载荷 → 渲染器配置的适配层（docs/architecture/campaign-rendering.md）。
 *
 * 只做三件事，不掺入业务判断：
 * 1. 字段改名（seoTitle/seoDescription → seo 嵌套结构，schemaVersion 收窄为 1）；
 * 2. 白名单过滤——服务端返回的 type 未必命中白名单，未命中一律剔除并记录，
 *    渲染器自身还有一层拒绝渲染，双保险不渲染任何未定义模块；
 * 3. props 只接受对象形态，缺省给空对象——渲染器只读 props 里的受控键，
 *    其余值（如非字符串）一律不渲染。
 */

export interface CampaignConfigResult {
  config: CampaignConfig
  /** 被剔除的模块：{id, type}，便于页面给出「部分内容未上线」的提示 */
  skipped: { id: string; type: string }[]
}

const WHITELIST: readonly string[] = CAMPAIGN_SECTION_TYPES

export function toCampaignConfig(page: RenderedPageView): CampaignConfigResult {
  const sections: CampaignSection[] = []
  const skipped: { id: string; type: string }[] = []

  for (const section of page.sections ?? []) {
    if (!WHITELIST.includes(section.type)) {
      skipped.push({ id: section.id, type: section.type })
      continue
    }
    const props =
      typeof section.props === 'object' && section.props !== null ? section.props : {}
    sections.push({
      id: section.id,
      type: section.type as CampaignSectionType,
      props,
    })
  }

  return {
    config: {
      schemaVersion: 1,
      template: page.template,
      seo: { title: page.seoTitle, description: page.seoDescription },
      sections,
    },
    skipped,
  }
}