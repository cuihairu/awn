import type { RenderedSection } from './mp-api'

/**
 * 小程序专场渲染契约子集（已代决 2026-10-14 巡检授权）。
 *
 * 白名单共 13 类（packages/campaign-renderer，schemaVersion 1）；
 * 小程序端先落文本优先的 7 类（View/Text 即可渲染，无媒体与图表依赖）：
 * heroBanner / richText / noticeBar / rulePanel / faq / priceTrend / countdown。
 * 媒体类（imageGallery / videoEmbed）与数据列表类（eventList / ticketGrid /
 * ticketComparison / channelComparison）后续随小程序图片/图表组件接入。
 * 未收录类型与白名单外类型一律跳过并告警（与 web 渲染器「拒绝渲染未知类型」同语义，
 * 禁止注入任意 HTML/JS；素材红线：缺图用占位说明文字，不生成图像代餐）。
 */

export const MP_SECTION_TYPES = [
  'heroBanner',
  'richText',
  'noticeBar',
  'rulePanel',
  'faq',
  'priceTrend',
  'countdown',
] as const

export type MpSectionType = (typeof MP_SECTION_TYPES)[number]

export interface NormalizedSection {
  id: string
  type: MpSectionType
  /** 标题（noticeBar 无标题） */
  title: string
  /** 正文段（richText / rulePanel） */
  paragraphs: string[]
  /** 键值条目（faq：q/a；countdown：until；priceTrend：summary；noticeBar：message） */
  entries: { key: string; value: string }[]
}

function text(props: Record<string, unknown>, key: string): string {
  const value = props[key]
  return typeof value === 'string' ? value : ''
}

function texts(props: Record<string, unknown>, key: string): string[] {
  const value = props[key]
  return Array.isArray(value) ? value.filter((v): v is string => typeof v === 'string') : []
}

function items(props: Record<string, unknown>, key: string): Record<string, unknown>[] {
  const value = props[key]
  return Array.isArray(value)
    ? value.filter((v): v is Record<string, unknown> => typeof v === 'object' && v !== null)
    : []
}

/** 单个 section → 渲染数据；白名单外或子集外 → null（调用方跳过并告警） */
export function normalizeSection(section: RenderedSection): NormalizedSection | null {
  const base = { id: section.id || section.type }
  switch (section.type) {
    case 'heroBanner':
      return {
        ...base,
        type: 'heroBanner',
        title: text(section.props, 'title'),
        paragraphs: [],
        entries: [{ key: 'subtitle', value: text(section.props, 'subtitle') }],
      }
    case 'richText':
      return {
        ...base,
        type: 'richText',
        title: text(section.props, 'title'),
        paragraphs: texts(section.props, 'paragraphs'),
        entries: [],
      }
    case 'noticeBar':
      return {
        ...base,
        type: 'noticeBar',
        title: '',
        paragraphs: [],
        entries: [{ key: 'message', value: text(section.props, 'message') }],
      }
    case 'rulePanel':
      return {
        ...base,
        type: 'rulePanel',
        title: text(section.props, 'title') || '购票须知',
        paragraphs: texts(section.props, 'rules'),
        entries: [],
      }
    case 'faq':
      return {
        ...base,
        type: 'faq',
        title: text(section.props, 'title') || '常见问题',
        paragraphs: [],
        entries: items(section.props, 'entries').map((item, index) => ({
          key: typeof item.q === 'string' ? item.q : `问题 ${index + 1}`,
          value: typeof item.a === 'string' ? item.a : '',
        })),
      }
    case 'priceTrend':
      return {
        ...base,
        type: 'priceTrend',
        title: text(section.props, 'title') || '价格趋势',
        paragraphs: [],
        entries: [{ key: 'summary', value: text(section.props, 'summary') }],
      }
    case 'countdown':
      return {
        ...base,
        type: 'countdown',
        title: text(section.props, 'title') || '倒计时',
        paragraphs: [],
        entries: [{ key: 'until', value: text(section.props, 'until') }],
      }
    default:
      return null
  }
}

export interface NormalizeResult {
  sections: NormalizedSection[]
  /** 被跳过的类型（子集外或白名单外），渲染层据此 console.warn */
  skipped: string[]
}

export function normalizeSections(list: RenderedSection[]): NormalizeResult {
  const sections: NormalizedSection[] = []
  const skipped: string[] = []
  for (const section of list ?? []) {
    const normalized = normalizeSection(section)
    if (normalized) sections.push(normalized)
    else skipped.push(section.type || '(missing type)')
  }
  return { sections, skipped }
}
